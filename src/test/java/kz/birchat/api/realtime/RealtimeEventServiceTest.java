package kz.birchat.api.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.service.ChatMessageActionsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimeEventServiceTest {

    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatMessageActionsService chatMessageActionsService;
    @Mock
    private CompanyMemberRepository companyMemberRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private WebSocketSession memberSession;
    @Mock
    private WebSocketSession outsiderSession;

    private final RealtimeSessionRegistry registry = new RealtimeSessionRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RealtimeEventService service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private ChatEntity chat;

    @BeforeEach
    void setUp() {
        service = new RealtimeEventService(registry, chatRepository, chatMessageRepository,
                chatMessageActionsService, companyMemberRepository, transactionManager, objectMapper);

        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);
        chat = new ChatEntity();
        chat.setId(UUID.randomUUID());
        chat.setCompany(company);

        lenient().when(memberSession.isOpen()).thenReturn(true);
        lenient().when(outsiderSession.isOpen()).thenReturn(true);
        registry.add(member, memberSession);
        registry.add(outsider, outsiderSession);
        lenient().when(chatMessageActionsService.recipients(companyId, chat)).thenReturn(new HashSet<>(Set.of(member)));
    }

    @Test
    void eventGoesOnlyToChatMembers() throws Exception {
        UUID messageId = UUID.randomUUID();

        service.deliver(() -> chat, "message.deleted", Map.of("messageId", messageId.toString()));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(memberSession).sendMessage(captor.capture());
        verify(outsiderSession, never()).sendMessage(any());

        JsonNode json = objectMapper.readTree(captor.getValue().getPayload());
        assertEquals("message.deleted", json.get("type").asText());
        assertEquals(companyId.toString(), json.get("companyId").asText());
        assertEquals(chat.getId().toString(), json.get("chatId").asText());
        assertEquals(messageId.toString(), json.get("messageId").asText());
    }

    @Test
    void brokenConnectionDoesNotStopOthers() throws Exception {
        WebSocketSession second = mock(WebSocketSession.class);
        when(second.isOpen()).thenReturn(true);
        registry.add(member, second);
        doThrow(new java.io.IOException("broken pipe")).when(memberSession).sendMessage(any());

        service.deliver(() -> chat, "chat.renamed", Map.of("name", "Склад"));

        verify(second).sendMessage(any());
    }

    @Test
    void removedMemberGetsCompanyRemovedAndColleaguesGetMemberRemoved() throws Exception {
        kz.birchat.api.entity.UserEntity colleague = new kz.birchat.api.entity.UserEntity();
        colleague.setId(member);
        kz.birchat.api.entity.CompanyMemberEntity colleagueMembership = new kz.birchat.api.entity.CompanyMemberEntity();
        colleagueMembership.setUser(colleague);
        when(companyMemberRepository.findByCompanyIdAndStatusOrderByJoinedAtAsc(companyId, "ACTIVE"))
                .thenReturn(java.util.List.of(colleagueMembership));

        service.deliverMemberRemoved(companyId, outsider);

        ArgumentCaptor<TextMessage> removed = ArgumentCaptor.forClass(TextMessage.class);
        verify(outsiderSession).sendMessage(removed.capture());
        JsonNode removedJson = objectMapper.readTree(removed.getValue().getPayload());
        assertEquals("company.removed", removedJson.get("type").asText());
        assertEquals(companyId.toString(), removedJson.get("companyId").asText());

        ArgumentCaptor<TextMessage> colleagues = ArgumentCaptor.forClass(TextMessage.class);
        verify(memberSession).sendMessage(colleagues.capture());
        JsonNode memberJson = objectMapper.readTree(colleagues.getValue().getPayload());
        assertEquals("member.removed", memberJson.get("type").asText());
        assertEquals(outsider.toString(), memberJson.get("userId").asText());
    }
}
