
package kz.birchat.api.service;

import jakarta.persistence.EntityManager;
import kz.birchat.api.ai.AiProviderResolver;
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
                () -> aiService.getTodayDirectorSummaryBeta(
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

        var response = aiService.getTodayDirectorSummaryBeta(
                companyId, directorId
        );

        assertNotNull(response);

        verifyNoInteractions(aiProviderResolver);
        verify(aiCompanySummaryRepository, never()).save(any());
    }
}
