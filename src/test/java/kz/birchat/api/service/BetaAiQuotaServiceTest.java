
package kz.birchat.api.service;

import kz.birchat.api.dto.AiAskRequest;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.BetaAiQuotaCounterRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BetaAiQuotaServiceTest {

    @Mock
    private BetaAiQuotaCounterRepository quotaRepository;

    @Mock
    private CompanyMemberRepository memberRepository;

    @InjectMocks
    private BetaAiQuotaService quotaService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void outsiderCannotConsumeQuota() {
        when(memberRepository.existsByCompanyIdAndUserIdAndStatus(
                companyId, userId, "ACTIVE"
        )).thenReturn(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> quotaService.consumeForAsk(
                        companyId, userId, validRequestWithoutStubbing()
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ApiErrorCode.NOT_A_MEMBER, exception.getCode());

        verifyNoInteractions(quotaRepository);
    }

    @Test
    void validUserConsumesThreeQuotas() {
        mockActiveMember();

        when(quotaRepository.incrementIfBelowLimit(
                anyString(), anyString(), any(Instant.class), anyInt()
        )).thenReturn(true);

        quotaService.consumeForAsk(
                companyId, userId, validRequest()
        );

        verify(quotaRepository, times(3))
                .incrementIfBelowLimit(
                        anyString(),
                        anyString(),
                        any(Instant.class),
                        anyInt()
                );

        verify(quotaRepository).incrementIfBelowLimit(
                eq("USER_MINUTE"),
                eq(companyId + ":" + userId),
                any(Instant.class),
                eq(5)
        );

        verify(quotaRepository).incrementIfBelowLimit(
                eq("USER_DAY"),
                eq(companyId + ":" + userId),
                any(Instant.class),
                eq(100)
        );

        verify(quotaRepository).incrementIfBelowLimit(
                eq("COMPANY_DAY"),
                eq(companyId.toString()),
                any(Instant.class),
                eq(500)
        );
    }

    @Test
    void companyQuotaExceededReturns429() {
        mockActiveMember();

        when(quotaRepository.incrementIfBelowLimit(
                anyString(), anyString(), any(Instant.class), anyInt()
        )).thenReturn(true, true, false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> quotaService.consumeForAsk(
                        companyId, userId, validRequest()
                )
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                exception.getStatus());

        assertEquals(ApiErrorCode.AI_QUOTA_EXCEEDED,
                exception.getCode());

        verify(quotaRepository, times(3))
                .incrementIfBelowLimit(
                        anyString(),
                        anyString(),
                        any(Instant.class),
                        anyInt()
                );
    }

    private void mockActiveMember() {
        when(memberRepository.existsByCompanyIdAndUserIdAndStatus(
                companyId, userId, "ACTIVE"
        )).thenReturn(true);
    }

    private AiAskRequest validRequest() {
        AiAskRequest request = mock(AiAskRequest.class);
        when(request.question()).thenReturn("Тестовый вопрос");
        return request;
    }

    private AiAskRequest validRequestWithoutStubbing() {
        return mock(AiAskRequest.class);
    }
}
