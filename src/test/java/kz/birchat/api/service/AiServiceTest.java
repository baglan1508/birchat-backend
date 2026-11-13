package kz.birchat.api.service;

import jakarta.persistence.EntityManager;
import kz.birchat.api.ai.AiProvider;
import kz.birchat.api.ai.AiProviderResolver;
import kz.birchat.api.ai.AiProviderResponse;
import kz.birchat.api.dto.AiAskResponse;
import kz.birchat.api.entity.AiMessageEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.repository.ChatAttachmentRepository;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.enums.ChatMessageType;
import kz.birchat.api.entity.AiThreadEntity;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import kz.birchat.api.config.AiProperties;
import kz.birchat.api.dto.AiAskRequest;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import kz.birchat.api.entity.AiCompanySummaryEntity;
import kz.birchat.api.entity.CompanyMemberEntity;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private AiThreadRepository aiThreadRepository;

    @Mock
    private AiMessageRepository aiMessageRepository;

    @Mock
    private AiCompanySummaryRepository aiCompanySummaryRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private AiProviderResolver aiProviderResolver;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ChatAttachmentRepository chatAttachmentRepository;

    @Mock
    private CompanyFileRepository companyFileRepository;

    @InjectMocks
    private AiService aiService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();

    @Test
    void outsiderCannotAskAi() {
        mockOutsider();

        AiAskRequest request = mock(AiAskRequest.class);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> aiService.ask(companyId, outsiderId, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.NOT_A_MEMBER, exception.getCode());

        verifyNoInteractions(aiProviderResolver);
        verifyNoInteractions(aiThreadRepository);
        verifyNoInteractions(aiMessageRepository);
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void outsiderCannotReadAiHistory() {
        mockOutsider();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> aiService.getHistory(companyId, outsiderId, 20)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.NOT_A_MEMBER, exception.getCode());

        verifyNoInteractions(aiThreadRepository);
        verifyNoInteractions(aiMessageRepository);
        verifyNoInteractions(aiProviderResolver);
    }

    private void mockOutsider() {
        when(companyMemberRepository
                .existsByCompanyIdAndUserIdAndStatus(
                        companyId, outsiderId, "ACTIVE"
                ))
                .thenReturn(false);
    }

    @Test
    void adminCannotGetDirectorSummary() {
        UUID companyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        CompanyMemberEntity member =
                mock(CompanyMemberEntity.class, RETURNS_DEEP_STUBS);

        when(member.getRole().getCode()).thenReturn("ADMIN");

        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(
                companyId, adminId, "ACTIVE"
        )).thenReturn(Optional.of(member));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> aiService.getTodayDirectorSummary(
                        companyId, adminId
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.FORBIDDEN, exception.getCode());

        verifyNoInteractions(aiCompanySummaryRepository);
        verifyNoInteractions(chatMessageRepository);
        verifyNoInteractions(aiProviderResolver);
    }

    @Test
    void directorCanGetDirectorSummary() {
        UUID companyId = UUID.randomUUID();
        UUID directorId = UUID.randomUUID();

        CompanyMemberEntity member =
                mock(CompanyMemberEntity.class, RETURNS_DEEP_STUBS);

        when(member.getRole().getCode()).thenReturn("DIRECTOR");

        when(companyMemberRepository.findByCompanyIdAndUserIdAndStatus(
                companyId, directorId, "ACTIVE"
        )).thenReturn(Optional.of(member));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        AiCompanySummaryEntity summary =
                mock(AiCompanySummaryEntity.class);

        when(summary.getPeriodEnd()).thenReturn(now);
        when(summary.getGeneratedAt()).thenReturn(now);
        when(summary.getTitle()).thenReturn("Сводка за сегодня");
        when(summary.getSummary()).thenReturn("Тестовая сводка");
        when(summary.getItemsText()).thenReturn("");

        when(aiCompanySummaryRepository
                .findFirstByCompanyIdAndGeneratedAtAfterOrderByGeneratedAtDesc(
                        eq(companyId),
                        any(LocalDateTime.class)
                ))
                .thenReturn(Optional.of(summary));

        when(chatMessageRepository
                .countGeneralChatMessagesAfterForAiSummary(
                        companyId, now
                ))
                .thenReturn(0L);

        var response = aiService.getTodayDirectorSummary(
                companyId, directorId
        );

        assertNotNull(response);

        verifyNoInteractions(aiProviderResolver);
        verify(aiCompanySummaryRepository, never()).save(any());
    }

    @Test
    void modelIsCalledOnlyAfterReadTransactionIsClosed() {
        UUID userId = UUID.randomUUID();
        AiProvider provider = mockMemberAndProvider(userId);

        when(provider.ask(any())).thenReturn(new AiProviderResponse("Ответ", "mock"));
        when(aiThreadRepository.findDefaultThread(companyId, userId))
                .thenReturn(Optional.of(new AiThreadEntity()));
        when(aiMessageRepository.save(any(AiMessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AiAskResponse response = aiService.ask(companyId, userId, new AiAskRequest("Что нового?"));

        assertEquals("Ответ", response.answer());

        InOrder order = inOrder(transactionManager, provider);
        order.verify(transactionManager).commit(any());
        order.verify(provider).ask(any());
        order.verify(transactionManager).getTransaction(any());

        // вопрос и ответ
        verify(aiMessageRepository, times(2)).save(any(AiMessageEntity.class));
    }

    @Test
    void nothingIsSavedWhenModelFails() {
        UUID userId = UUID.randomUUID();
        AiProvider provider = mockMemberAndProvider(userId);

        when(provider.ask(any())).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.INTERNAL_ERROR, "AI недоступен"));

        assertThrows(ApiException.class,
                () -> aiService.ask(companyId, userId, new AiAskRequest("Что нового?")));

        verifyNoInteractions(aiThreadRepository, aiMessageRepository);
    }

    private AiProvider mockMemberAndProvider(UUID userId) {
        when(companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(true);
        when(chatMessageRepository.findLatestGeneralChatMessagesForAiContext(eq(companyId), any()))
                .thenReturn(List.of());

        AiProvider provider = mock(AiProvider.class);
        when(aiProviderResolver.resolve()).thenReturn(provider);
        return provider;
    }

    @Test
    void photoWithoutCaptionIsVisibleToAi() {
        ChatMessageEntity photo = new ChatMessageEntity();
        photo.setType(ChatMessageType.IMAGE);
        photo.setContent("");

        assertTrue(AiService.hasAiContent(photo));
        assertEquals("[фото]", AiService.aiContent(photo));
    }

    @Test
    void attachmentCaptionFollowsLabel() {
        ChatMessageEntity document = new ChatMessageEntity();
        document.setType(ChatMessageType.DOCUMENT);
        document.setContent(" Накладная ");

        assertEquals("[документ] Накладная", AiService.aiContent(document));
    }

    @Test
    void emptyTextMessageIsSkipped() {
        ChatMessageEntity empty = new ChatMessageEntity();
        empty.setType(ChatMessageType.TEXT);
        empty.setContent("  ");

        assertFalse(AiService.hasAiContent(empty));
    }

    @Test
    void photoDescriptionGoesIntoLabel() {
        ChatMessageEntity photo = new ChatMessageEntity();
        photo.setType(ChatMessageType.IMAGE);
        photo.setContent("Вот накладная");

        assertEquals("[фото: Накладная №15 от ТОО «Арман», 150 000 тг] Вот накладная",
                AiService.aiContent(photo, "Накладная №15 от ТОО «Арман», 150 000 тг"));
    }

    @Test
    void documentNameAndTextReachAi() {
        kz.birchat.api.entity.CompanyFileEntity receipt = new kz.birchat.api.entity.CompanyFileEntity();
        receipt.setOriginalFileName("Чек Kaspi.pdf");
        receipt.setAiStatus(FileAnalysisService.AI_DONE);
        receipt.setAiText("PDF, 1 стр.:\nИтого 1 150 тг");

        assertEquals("«Чек Kaspi.pdf» — PDF, 1 стр.:\nИтого 1 150 тг",
                AiService.mediaText(ChatMessageType.DOCUMENT, receipt));
    }

    @Test
    void unreadDocumentIsKnownByName() {
        kz.birchat.api.entity.CompanyFileEntity contract = new kz.birchat.api.entity.CompanyFileEntity();
        contract.setOriginalFileName("Договор аренды.docx");

        assertEquals("«Договор аренды.docx»", AiService.mediaText(ChatMessageType.DOCUMENT, contract));
    }

    @Test
    void photoWithoutDescriptionAddsNothing() {
        kz.birchat.api.entity.CompanyFileEntity photo = new kz.birchat.api.entity.CompanyFileEntity();
        photo.setOriginalFileName("IMG_2041.jpg");
        photo.setAiStatus(FileAnalysisService.AI_PENDING);

        assertNull(AiService.mediaText(ChatMessageType.IMAGE, photo));
    }

    @Test
    void historyShowsOnlyTodaysDialog() {
        UUID userId = UUID.randomUUID();
        AiThreadEntity thread = new AiThreadEntity();
        thread.setId(UUID.randomUUID());
        when(companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(true);
        when(aiThreadRepository.findDefaultThread(companyId, userId)).thenReturn(Optional.of(thread));
        org.mockito.ArgumentCaptor<java.time.LocalDateTime> since =
                org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        when(aiMessageRepository.findLatestByThreadIdSince(eq(thread.getId()), since.capture(), any()))
                .thenReturn(List.of());

        aiService.getHistory(companyId, userId, 50, "oldest");

        // Полночь по Алматы (UTC+5) — 19:00 UTC предыдущего дня, не раньше чем сутки назад.
        java.time.LocalDateTime nowUtc = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        assertEquals(19, since.getValue().getHour());
        assertEquals(0, since.getValue().getMinute());
        assertTrue(since.getValue().isAfter(nowUtc.minusDays(1)));
        assertFalse(since.getValue().isAfter(nowUtc));
        verify(aiMessageRepository, never()).findLatestByThreadId(any(), any());
    }

    @Test
    void newestQuestionsGoFirstButQuestionStaysAboveAnswer() {
        AiMessageEntity q1 = aiMessage(kz.birchat.api.enums.AiMessageRole.USER, "Что обсуждали?", 1);
        AiMessageEntity a1 = aiMessage(kz.birchat.api.enums.AiMessageRole.ASSISTANT, "Короткая беседа", 2);
        AiMessageEntity q2 = aiMessage(kz.birchat.api.enums.AiMessageRole.USER, "Было ли фото?", 3);
        AiMessageEntity a2 = aiMessage(kz.birchat.api.enums.AiMessageRole.ASSISTANT, "Да, три фото", 4);

        assertEquals(List.of(q2, a2, q1, a1), AiService.newestExchangesFirst(List.of(q1, a1, q2, a2)));
    }

    @Test
    void unknownOrderIsRejected() {
        UUID userId = UUID.randomUUID();
        when(companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> aiService.getHistory(companyId, userId, 50, "sideways"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void newDialogDeletesOwnQuestionsAndAnswers() {
        UUID userId = UUID.randomUUID();
        AiThreadEntity thread = new AiThreadEntity();
        thread.setId(UUID.randomUUID());
        when(companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(companyId, userId, "ACTIVE"))
                .thenReturn(true);
        when(aiThreadRepository.findDefaultThread(companyId, userId)).thenReturn(Optional.of(thread));

        aiService.clearHistory(companyId, userId);

        verify(aiMessageRepository).deleteByThreadId(thread.getId());
    }

    private static AiMessageEntity aiMessage(kz.birchat.api.enums.AiMessageRole role, String content, int minute) {
        AiMessageEntity message = new AiMessageEntity();
        message.setId(UUID.randomUUID());
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(java.time.LocalDateTime.of(2026, 11, 13, 9, minute));
        return message;
    }

    @Test
    void voiceAndUnprocessedPhotoDoNotBreakAiContext() {
        // Регрессия: голосовое (текста нет) давало Map.entry(id, null) → NullPointerException
        // в вопросах к AI и в сводке.
        ChatMessageEntity voice = typedMessage(ChatMessageType.VOICE);
        ChatMessageEntity photo = typedMessage(ChatMessageType.IMAGE);
        ChatMessageEntity receipt = typedMessage(ChatMessageType.DOCUMENT);

        kz.birchat.api.entity.CompanyFileEntity voiceFile = new kz.birchat.api.entity.CompanyFileEntity();
        voiceFile.setOriginalFileName("voice.m4a");
        kz.birchat.api.entity.CompanyFileEntity photoFile = new kz.birchat.api.entity.CompanyFileEntity();
        photoFile.setAiStatus(FileAnalysisService.AI_PENDING);
        kz.birchat.api.entity.CompanyFileEntity receiptFile = new kz.birchat.api.entity.CompanyFileEntity();
        receiptFile.setOriginalFileName("Чек.pdf");
        receiptFile.setAiStatus(FileAnalysisService.AI_DONE);
        receiptFile.setAiText("PDF, 1 стр.: Итого 1 150 тг");

        when(chatAttachmentRepository.findByMessageIds(any())).thenReturn(List.of(
                attachment(voice, voiceFile),
                attachment(photo, photoFile),
                attachment(receipt, receiptFile)
        ));

        java.util.Map<UUID, String> texts = aiService.loadMediaTexts(List.of(voice, photo, receipt));

        assertEquals(java.util.Map.of(receipt.getId(), "«Чек.pdf» — PDF, 1 стр.: Итого 1 150 тг"), texts);
        assertEquals("[голосовое сообщение]", AiService.aiContent(voice, texts.get(voice.getId())));
    }

    private static ChatMessageEntity typedMessage(ChatMessageType type) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(UUID.randomUUID());
        message.setType(type);
        message.setContent("");
        return message;
    }

    private static kz.birchat.api.entity.ChatAttachmentEntity attachment(
            ChatMessageEntity message,
            kz.birchat.api.entity.CompanyFileEntity file
    ) {
        kz.birchat.api.entity.ChatAttachmentEntity attachment = new kz.birchat.api.entity.ChatAttachmentEntity();
        attachment.setMessage(message);
        attachment.setFile(file);
        return attachment;
    }
}
