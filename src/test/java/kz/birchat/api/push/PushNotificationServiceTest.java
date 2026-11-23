package kz.birchat.api.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.birchat.api.dto.ChatSummaryResponse;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.ChatMessageType;
import kz.birchat.api.repository.ChatAttachmentRepository;
import kz.birchat.api.repository.ChatMessageMentionRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.service.ChatManagementService;
import kz.birchat.api.service.ChatMessageActionsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private PushSender pushSender;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatAttachmentRepository chatAttachmentRepository;
    @Mock
    private CompanyMemberRepository companyMemberRepository;
    @Mock
    private ChatMessageActionsService chatMessageActionsService;
    @Mock
    private ChatMessageMentionRepository chatMessageMentionRepository;
    @Mock
    private FcmClient fcmClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final ChatPresenceTracker presenceTracker = new ChatPresenceTracker();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PushNotificationService service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final UUID reader = UUID.randomUUID();
    private final UUID away = UUID.randomUUID();

    private ChatEntity chat;
    private ChatMessageEntity message;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(pushSender, deviceTokenRepository, presenceTracker,
                chatMessageRepository, chatAttachmentRepository, companyMemberRepository,
                chatMessageActionsService, chatMessageMentionRepository, fcmClient,
                transactionManager, objectMapper);
        lenient().when(pushSender.isConfigured()).thenReturn(true);

        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);
        company.setName("Nomad Trade");

        chat = new ChatEntity();
        chat.setId(UUID.randomUUID());
        chat.setCompany(company);
        chat.setType("GROUP");
        chat.setName("Склад Актобе");

        UserEntity user = new UserEntity();
        user.setId(author);
        user.setDisplayName("Нурлан");

        message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setChat(chat);
        message.setUser(user);
        message.setType(ChatMessageType.TEXT);
        message.setContent("По оплате ждём подтверждение до пятницы");
        message.setIsDeleted(false);

        lenient().when(chatMessageRepository.findAllWithUserAndChatByIdIn(List.of(message.getId())))
                .thenReturn(List.of(message));
        lenient().when(chatMessageActionsService.recipients(companyId, chat))
                .thenAnswer(invocation -> new HashSet<>(Set.of(author, reader, away)));
        lenient().when(chatAttachmentRepository.findByMessageIds(any())).thenReturn(List.of());
    }

    @Test
    void pushGoesToMembersExceptAuthorAndThoseWithChatOpen() throws Exception {
        presenceTracker.touchChat(reader, chat.getId());
        when(deviceTokenRepository.findByUserIdIn(Set.of(away))).thenReturn(List.of(device(away, "aa11", "production", "ru")));
        when(chatMessageRepository.countUnreadTotal(away)).thenReturn(7L);

        List<PushNotificationService.Outgoing> notifications = service.prepare(message.getId());

        assertEquals(1, notifications.size());
        assertEquals("aa11", notifications.get(0).deviceToken());
        assertEquals("production", notifications.get(0).environment());

        JsonNode json = objectMapper.readTree(notifications.get(0).payloadJson());
        assertEquals("Nomad Trade", json.at("/aps/alert/title").asText());
        assertEquals("Склад Актобе", json.at("/aps/alert/subtitle").asText());
        assertEquals("Нурлан: По оплате ждём подтверждение до пятницы", json.at("/aps/alert/body").asText());
        assertEquals(7, json.at("/aps/badge").asInt());
        assertEquals("default", json.at("/aps/sound").asText());
        assertEquals(chat.getId().toString(), json.at("/aps/thread-id").asText());
        assertEquals(companyId.toString(), json.get("companyId").asText());
        assertEquals(chat.getId().toString(), json.get("chatId").asText());
        assertEquals(message.getId().toString(), json.get("messageId").asText());
    }

    @Test
    void generalChatHasNoSubtitle() {
        chat.setType("GENERAL");
        chat.setName("Общий");

        var payload = PushNotificationService.payload("Nomad Trade", null, "Нурлан: привет", 1,
                companyId, chat.getId(), message.getId());

        assertFalse(((java.util.Map<?, ?>) ((java.util.Map<?, ?>) payload.get("aps")).get("alert")).containsKey("subtitle"));
    }

    @Test
    void deletedMessageIsNotPushed() {
        message.setIsDeleted(true);

        assertTrue(service.prepare(message.getId()).isEmpty());
        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void nothingIsQueuedWhileApnsKeyIsNotSet() {
        when(pushSender.isConfigured()).thenReturn(false);

        service.messageCreated(message.getId());

        verify(pushSender, never()).send(anyString(), anyString(), anyString());
        verifyNoInteractions(chatMessageRepository);
    }

    private static DeviceTokenEntity device(UUID userId, String token, String environment, String locale) {
        DeviceTokenEntity device = new DeviceTokenEntity();
        device.setUserId(userId);
        device.setToken(token);
        device.setEnvironment(environment);
        device.setLocale(locale);
        return device;
    }

    @Test
    void mentionedUserGetsPushEvenWithChatOpen() throws Exception {
        presenceTracker.touchChat(reader, chat.getId());
        UserEntity mentionedUser = new UserEntity();
        mentionedUser.setId(reader);
        kz.birchat.api.entity.ChatMessageMentionEntity mention = new kz.birchat.api.entity.ChatMessageMentionEntity();
        mention.setUser(mentionedUser);
        when(chatMessageMentionRepository.findByMessageIds(List.of(message.getId()))).thenReturn(List.of(mention));
        when(deviceTokenRepository.findByUserIdIn(Set.of(reader, away))).thenReturn(List.of(
                device(reader, "bb22", "production", "kk"),
                device(away, "aa11", "production", "ru")
        ));

        List<PushNotificationService.Outgoing> notifications = service.prepare(message.getId());

        assertEquals(2, notifications.size());
        JsonNode mentioned = objectMapper.readTree(notifications.get(0).payloadJson());
        assertEquals("Нурлан сізді атап өтті: По оплате ждём подтверждение до пятницы",
                mentioned.at("/aps/alert/body").asText());
        JsonNode regular = objectMapper.readTree(notifications.get(1).payloadJson());
        assertEquals("Нурлан: По оплате ждём подтверждение до пятницы", regular.at("/aps/alert/body").asText());
    }

    @Test
    void directChatPushHasAuthorAsTitle() throws Exception {
        chat.setType("DIRECT");
        chat.setName(null);
        when(deviceTokenRepository.findByUserIdIn(Set.of(reader, away))).thenReturn(List.of(
                device(away, "aa11", "production", "ru")
        ));

        List<PushNotificationService.Outgoing> notifications = service.prepare(message.getId());

        JsonNode json = objectMapper.readTree(notifications.get(0).payloadJson());
        assertEquals("Нурлан", json.at("/aps/alert/title").asText());
        assertTrue(json.at("/aps/alert/subtitle").isMissingNode());
        assertEquals("По оплате ждём подтверждение до пятницы", json.at("/aps/alert/body").asText());
    }

    @Test
    void androidGetsFcmMessageWithGroupInTitle() throws Exception {
        when(fcmClient.isConfigured()).thenReturn(true);
        DeviceTokenEntity android = device(away, "fcm:Token_AbC", "production", "ru");
        android.setPlatform("ANDROID");
        when(deviceTokenRepository.findByUserIdIn(Set.of(reader, away))).thenReturn(List.of(android));

        List<PushNotificationService.Outgoing> notifications = service.prepare(message.getId());

        assertEquals(1, notifications.size());
        assertEquals("ANDROID", notifications.get(0).platform());
        JsonNode json = objectMapper.readTree(notifications.get(0).payloadJson());
        assertEquals("Nomad Trade · Склад Актобе", json.at("/notification/title").asText());
        assertEquals("Нурлан: По оплате ждём подтверждение до пятницы", json.at("/notification/body").asText());
        assertEquals(chat.getId().toString(), json.at("/data/chatId").asText());
        assertEquals(json.at("/notification/title").asText(), json.at("/data/title").asText());
        assertEquals("HIGH", json.at("/android/priority").asText());
        assertEquals("messages", json.at("/android/notification/channel_id").asText());
        assertEquals(chat.getId().toString(), json.at("/android/notification/tag").asText());
        assertTrue(json.at("/token").isMissingNode());
    }

    @Test
    void androidIsSkippedWithoutFirebaseKey() {
        DeviceTokenEntity android = device(away, "fcmtoken", "production", "ru");
        android.setPlatform("ANDROID");
        when(deviceTokenRepository.findByUserIdIn(Set.of(reader, away))).thenReturn(List.of(android));

        assertTrue(service.prepare(message.getId()).isEmpty());
    }

    @Test
    void testPushReportsAppleAnswerPerDeviceAndRemovesDeadToken() {
        DeviceTokenEntity alive = device(author, "aa11bb22cc33", "sandbox", "kk");
        DeviceTokenEntity dead = device(author, "dd44", "production", "ru");
        DeviceTokenEntity android = device(author, "fcm:token", "production", "ru");
        android.setPlatform("ANDROID");
        when(deviceTokenRepository.findByUserIdIn(Set.of(author))).thenReturn(List.of(alive, dead, android));
        when(pushSender.deliver(eq("aa11bb22cc33"), eq("sandbox"), anyString()))
                .thenReturn(new PushSender.Delivery(PushSender.Result.SENT, 200, null));
        when(pushSender.deliver(eq("dd44"), eq("production"), anyString()))
                .thenReturn(new PushSender.Delivery(PushSender.Result.INVALID_TOKEN, 400, "BadDeviceToken"));

        List<PushNotificationService.TestPushResult> results = service.sendTest(author);

        assertEquals(3, results.size());
        assertEquals("SENT", results.get(0).result());
        assertEquals("…bb22cc33", results.get(0).tokenEnd());
        assertEquals("INVALID_TOKEN", results.get(1).result());
        assertEquals("BadDeviceToken", results.get(1).reason());
        assertTrue(results.get(1).removed());
        assertEquals("NOT_CONFIGURED", results.get(2).result());
        verify(deviceTokenRepository).deleteByTokenValue("dd44");

        kz.birchat.api.exception.ApiException tooOften = assertThrows(kz.birchat.api.exception.ApiException.class,
                () -> service.sendTest(author));
        assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, tooOften.getStatus());
    }

    @Test
    void badgeIsOneQuery() {
        when(chatMessageRepository.countUnreadTotal(away)).thenReturn(123_456L);

        assertEquals(9999, service.unreadTotal(away));
    }
}
