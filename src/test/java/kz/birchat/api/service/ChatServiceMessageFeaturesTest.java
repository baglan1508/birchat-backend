package kz.birchat.api.service;

import kz.birchat.api.dto.ChatMessageResponse;
import kz.birchat.api.dto.CreateChatMessageRequest;
import kz.birchat.api.dto.EditMessageRequest;
import kz.birchat.api.entity.ChatAttachmentEntity;
import kz.birchat.api.entity.ChatEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.ChatMessageMentionEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.ChatMessageType;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.ChatAttachmentRepository;
import kz.birchat.api.repository.ChatMessageMentionRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.ChatReadStateRepository;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceMessageFeaturesTest {

    @Mock
    private ChatAccessService chatAccessService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatReadStateRepository chatReadStateRepository;
    @Mock
    private CompanyFileRepository companyFileRepository;
    @Mock
    private ChatAttachmentRepository chatAttachmentRepository;
    @Mock
    private ChatMessageMentionRepository chatMessageMentionRepository;
    @Mock
    private ChatMessageActionsService chatMessageActionsService;
    @Mock
    private CompanyMemberRepository companyMemberRepository;
    @Mock
    private kz.birchat.api.repository.ChatMessageReactionRepository chatMessageReactionRepository;

    @InjectMocks
    private ChatService chatService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private CompanyEntity company;
    private ChatEntity chat;
    private UserEntity author;

    @BeforeEach
    void setUp() {
        company = new CompanyEntity();
        company.setId(companyId);
        chat = chat("GENERAL");
        author = user(authorId, "Нурлан");
    }

    @Test
    void replyCarriesShortPreviewOfOriginal() {
        mockWritable();
        ChatMessageEntity original = message(chat, user(UUID.randomUUID(), "Айгерим"), "а".repeat(150));
        when(chatMessageRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(chatMessageRepository.findAllWithUserAndChatByIdIn(Set.of(original.getId()))).thenReturn(List.of(original));
        mockSave();

        ChatMessageResponse response = chatService.createMessage(companyId, chat.getId(), authorId,
                new CreateChatMessageRequest("Да, до пятницы", original.getId(), null, null));

        assertEquals("Да, до пятницы", response.text());
        assertEquals(original.getId(), response.replyTo().id());
        assertEquals("Айгерим", response.replyTo().authorName());
        assertEquals(101, response.replyTo().text().length());
        assertTrue(response.replyTo().text().endsWith("…"));
        assertFalse(response.replyTo().deleted());
    }

    @Test
    void replyToMessageFromAnotherChatIsNotFound() {
        mockWritable();
        ChatMessageEntity foreign = message(chat("GROUP"), author, "Чужое");
        when(chatMessageRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        ApiException error = assertThrows(ApiException.class, () -> chatService.createMessage(companyId, chat.getId(),
                authorId, new CreateChatMessageRequest("Ответ", foreign.getId(), null, null)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals(ApiErrorCode.MESSAGE_NOT_FOUND, error.getCode());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void forwardCopiesTextTypeAndAttachment() {
        mockWritable();
        ChatEntity group = chat("GROUP");
        UserEntity originalAuthor = user(UUID.randomUUID(), "Айгерим");
        ChatMessageEntity source = message(group, originalAuthor, "Накладная");
        source.setType(ChatMessageType.DOCUMENT);
        when(chatMessageRepository.findAllWithUserAndChatByIdIn(List.of(source.getId()))).thenReturn(List.of(source));
        when(chatMessageRepository.findAllWithUserAndChatByIdIn(Set.of(source.getId()))).thenReturn(List.of(source));

        CompanyFileEntity file = new CompanyFileEntity();
        file.setId(UUID.randomUUID());
        ChatAttachmentEntity attachment = new ChatAttachmentEntity();
        attachment.setFile(file);
        attachment.setMessage(source);
        when(chatAttachmentRepository.findByMessageIds(List.of(source.getId()))).thenReturn(List.of(attachment));
        mockSave();

        ChatMessageResponse response = chatService.createMessage(companyId, chat.getId(), authorId,
                new CreateChatMessageRequest(null, null, source.getId(), null));

        verify(chatAccessService).requireReadableChat(companyId, group.getId(), authorId);
        assertEquals("Накладная", response.text());
        assertEquals("DOCUMENT", response.type());
        assertEquals(source.getId(), response.forwardedFrom().messageId());
        assertEquals("Айгерим", response.forwardedFrom().authorName());

        ArgumentCaptor<ChatAttachmentEntity> saved = ArgumentCaptor.forClass(ChatAttachmentEntity.class);
        verify(chatAttachmentRepository).save(saved.capture());
        assertSame(file, saved.getValue().getFile());
        assertSame(chat, saved.getValue().getChat());
    }

    @Test
    void emptyTextWithoutForwardIsRejected() {
        mockWritable();

        ApiException error = assertThrows(ApiException.class, () -> chatService.createMessage(companyId, chat.getId(),
                authorId, new CreateChatMessageRequest("   ", null, null, null)));

        assertEquals(ApiErrorCode.VALIDATION, error.getCode());
    }

    @Test
    void onlyChatParticipantsAreMentioned() {
        mockWritable();
        UUID colleague = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        when(chatMessageActionsService.recipients(companyId, chat)).thenReturn(new HashSet<>(Set.of(authorId, colleague)));
        when(userRepository.getReferenceById(colleague)).thenReturn(user(colleague, "Айгерим"));
        mockSave();

        chatService.createMessage(companyId, chat.getId(), authorId,
                new CreateChatMessageRequest("@Айгерим глянь счёт", null, null, List.of(colleague, outsider, authorId)));

        ArgumentCaptor<ChatMessageMentionEntity> mention = ArgumentCaptor.forClass(ChatMessageMentionEntity.class);
        verify(chatMessageMentionRepository, times(1)).save(mention.capture());
        assertEquals(colleague, mention.getValue().getUser().getId());
    }

    @Test
    void authorEditsWithin48Hours() {
        ChatMessageEntity message = message(chat, author, "Старый текст");
        mockReadableMessage(message);
        when(chatMessageRepository.save(message)).thenReturn(message);

        ChatMessageResponse response = chatService.editMessage(companyId, chat.getId(), message.getId(), authorId,
                new EditMessageRequest("  Новый текст  ", null));

        assertEquals("Новый текст", response.text());
        assertNotNull(response.editedAt());
        verify(chatMessageMentionRepository, never()).deleteByMessageId(any());
    }

    @Test
    void colleagueCannotEditSomeoneElsesMessage() {
        ChatMessageEntity message = message(chat, user(UUID.randomUUID(), "Айгерим"), "Текст");
        mockReadableMessage(message);

        ApiException error = assertThrows(ApiException.class, () -> chatService.editMessage(companyId, chat.getId(),
                message.getId(), authorId, new EditMessageRequest("Изменил", null)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals(ApiErrorCode.FORBIDDEN, error.getCode());
    }

    @Test
    void editAfter48HoursIsExpired() {
        ChatMessageEntity message = message(chat, author, "Текст");
        message.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(49));
        mockReadableMessage(message);

        ApiException error = assertThrows(ApiException.class, () -> chatService.editMessage(companyId, chat.getId(),
                message.getId(), authorId, new EditMessageRequest("Изменил", null)));

        assertEquals(ApiErrorCode.EDIT_EXPIRED, error.getCode());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void textMessageCannotBeEditedToEmpty() {
        ChatMessageEntity message = message(chat, author, "Текст");
        mockReadableMessage(message);

        ApiException error = assertThrows(ApiException.class, () -> chatService.editMessage(companyId, chat.getId(),
                message.getId(), authorId, new EditMessageRequest(" ", null)));

        assertEquals(ApiErrorCode.VALIDATION, error.getCode());
    }

    @Test
    void updatedWithoutSinceReturnsServerTime() {
        when(chatAccessService.requireReadableChat(companyId, chat.getId(), authorId)).thenReturn(chat);

        var response = chatService.getUpdatedSince(companyId, chat.getId(), authorId, null);

        assertTrue(response.messages().isEmpty());
        assertNotNull(response.checkedAt());
        verify(chatMessageRepository, never()).findEditedSince(any(), any(), any());
    }

    private void mockWritable() {
        when(chatAccessService.requireWritableChat(companyId, chat.getId(), authorId)).thenReturn(chat);
        lenient().when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
    }

    private void mockReadableMessage(ChatMessageEntity message) {
        when(chatAccessService.requireReadableChat(companyId, chat.getId(), authorId)).thenReturn(chat);
        when(chatMessageRepository.findById(message.getId())).thenReturn(Optional.of(message));
    }

    private void mockSave() {
        when(chatMessageRepository.save(any(ChatMessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ChatEntity chat(String type) {
        ChatEntity chat = new ChatEntity();
        chat.setId(UUID.randomUUID());
        chat.setCompany(company);
        chat.setType(type);
        return chat;
    }

    private UserEntity user(UUID id, String name) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFullName(name);
        user.setDisplayName(name);
        user.setInitials(name.substring(0, 1));
        return user;
    }

    private ChatMessageEntity message(ChatEntity chat, UserEntity user, String content) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setCompany(company);
        message.setChat(chat);
        message.setUser(user);
        message.setType(ChatMessageType.TEXT);
        message.setContent(content);
        message.setIsDeleted(false);
        message.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        return message;
    }

    @Test
    void reactionsAreCountedAndMarkedForViewer() {
        UUID colleague = UUID.randomUUID();
        ChatMessageEntity message = message(chat, author, "Готово");
        kz.birchat.api.entity.ChatMessageReactionEntity mine = reaction(message, authorId, "👍");
        kz.birchat.api.entity.ChatMessageReactionEntity theirs = reaction(message, colleague, "👍");
        kz.birchat.api.entity.ChatMessageReactionEntity heart = reaction(message, UUID.randomUUID(), "❤️");
        when(chatMessageReactionRepository.findByMessageIds(List.of(message.getId())))
                .thenReturn(List.of(mine, theirs, heart));

        ChatMessageResponse response = chatService.toResponses(List.of(message), authorId).get(0);

        assertEquals(2, response.reactions().size());
        assertEquals("👍", response.reactions().get(0).emoji());
        assertEquals(2, response.reactions().get(0).count());
        assertTrue(response.reactions().get(0).mine());
        assertFalse(response.reactions().get(1).mine());
        assertFalse(response.withoutMine().reactions().get(0).mine());
    }

    @Test
    void aroundReturnsHalfBeforeTargetAndRestAfter() {
        ChatMessageEntity target = message(chat, author, "Нашли в поиске");
        when(chatAccessService.requireReadableChat(companyId, chat.getId(), authorId)).thenReturn(chat);
        when(chatMessageRepository.findById(target.getId())).thenReturn(Optional.of(target));
        ChatMessageEntity older1 = message(chat, author, "старее 1");
        ChatMessageEntity older2 = message(chat, author, "старее 2");
        ChatMessageEntity newer = message(chat, author, "новее");
        // до — по убыванию времени, как отдаёт база
        when(chatMessageRepository.findGeneralChatMessagesBefore(eq(companyId), eq(chat.getId()), any(), eq(target.getId()), any()))
                .thenReturn(List.of(older2, older1));
        when(chatMessageRepository.findGeneralChatMessagesAfter(eq(companyId), eq(chat.getId()), any(), eq(target.getId()), any()))
                .thenReturn(List.of(newer));

        List<ChatMessageResponse> page = chatService.getMessages(companyId, chat.getId(), authorId, null, null, target.getId(), 4);

        assertEquals(List.of("старее 1", "старее 2", "Нашли в поиске", "новее"),
                page.stream().map(ChatMessageResponse::text).toList());
    }

    @Test
    void aroundCannotBeCombinedWithAfter() {
        when(chatAccessService.requireReadableChat(companyId, chat.getId(), authorId)).thenReturn(chat);

        ApiException error = assertThrows(ApiException.class, () -> chatService.getMessages(
                companyId, chat.getId(), authorId, UUID.randomUUID(), null, UUID.randomUUID(), 10));

        assertEquals(ApiErrorCode.VALIDATION, error.getCode());
    }

    private kz.birchat.api.entity.ChatMessageReactionEntity reaction(ChatMessageEntity message, UUID userId, String emoji) {
        kz.birchat.api.entity.ChatMessageReactionEntity reaction = new kz.birchat.api.entity.ChatMessageReactionEntity();
        reaction.setId(UUID.randomUUID());
        reaction.setMessage(message);
        reaction.setUser(user(userId, "Кто-то"));
        reaction.setEmoji(emoji);
        return reaction;
    }
}
