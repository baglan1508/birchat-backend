package kz.birchat.api.service;

import kz.birchat.api.dto.AiAskRequest;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.AiQuotaCounterRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiQuotaService {

    private static final int USER_MINUTE_LIMIT = 5;
    private static final int USER_DAY_LIMIT = 100;
    private static final int COMPANY_DAY_LIMIT = 500;

    private final AiQuotaCounterRepository quotaRepository;
    private final CompanyMemberRepository memberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeForAsk(
            UUID companyId,
            UUID userId,
            AiAskRequest request
    ) {
        boolean activeMember = memberRepository
                .existsByCompanyIdAndUserIdAndStatus(
                        companyId, userId, "ACTIVE"
                );

        if (!activeMember) {
            throw ApiException.forbidden(
                    ApiErrorCode.NOT_A_MEMBER,
                    "Пользователь не состоит в компании"
            );
        }

        validateQuestion(request);

        Instant now = Instant.now();
        Instant minuteStart = now.truncatedTo(ChronoUnit.MINUTES);
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);

        String userKey = companyId + ":" + userId;
        String companyKey = companyId.toString();

        consume(
                "USER_MINUTE",
                userKey,
                minuteStart,
                USER_MINUTE_LIMIT,
                "Превышен лимит: 5 AI-запросов в минуту"
        );

        consume(
                "USER_DAY",
                userKey,
                dayStart,
                USER_DAY_LIMIT,
                "Превышен суточный лимит AI-запросов пользователя"
        );

        consume(
                "COMPANY_DAY",
                companyKey,
                dayStart,
                COMPANY_DAY_LIMIT,
                "Превышен суточный лимит AI-запросов компании"
        );
    }

    private void validateQuestion(AiAskRequest request) {
        if (request == null
                || request.question() == null
                || request.question().isBlank()) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "question обязателен"
            );
        }

        if (request.question().trim().length() > 5000) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "question не должен превышать 5000 символов"
            );
        }
    }

    private void consume(
            String scope,
            String subjectKey,
            Instant windowStart,
            int limit,
            String errorMessage
    ) {
        boolean allowed = quotaRepository.incrementIfBelowLimit(
                scope,
                subjectKey,
                windowStart,
                limit
        );

        if (!allowed) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ApiErrorCode.AI_QUOTA_EXCEEDED,
                    errorMessage
            );
        }
    }
}
