package kz.birchat.api.service;

import kz.birchat.api.dto.AiAskRequest;
import kz.birchat.api.dto.AiAskResponse;
import kz.birchat.api.dto.AiDirectorSummaryResponse;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MOCK_MODEL = "mock";

    private final CompanyMemberRepository companyMemberRepository;

    @Transactional(readOnly = true)
    public AiAskResponse ask(
            UUID companyId,
            UUID userId,
            AiAskRequest request
    ) {
        checkActiveMember(companyId, userId);

        String question = normalizeQuestion(request);

        String answer = """
                AI mock: я пока работаю в тестовом режиме.
                Позже здесь будет ответ на основе сообщений, файлов и памяти компании.

                Ваш вопрос: %s
                """.formatted(question);

        return new AiAskResponse(
                answer,
                MOCK_MODEL,
                TimeUtils.utcOffsetNow()
        );
    }

    @Transactional(readOnly = true)
    public AiDirectorSummaryResponse getTodayDirectorSummary(
            UUID companyId,
            UUID userId
    ) {
        checkActiveMember(companyId, userId);

        return new AiDirectorSummaryResponse(
                "Сводка за сегодня",
                "AI mock: позже здесь будет краткая сводка по сообщениям, файлам и активности компании за сегодня.",
                List.of(
                        "Сообщения общего чата будут анализироваться позже",
                        "Файлы компании будут учитываться позже",
                        "Память компании будет добавлена отдельным этапом",
                        "Интеграция с OpenAI будет подключена после mock-этапа"
                ),
                TimeUtils.utcOffsetNow()
        );
    }

    private void checkActiveMember(UUID companyId, UUID userId) {
        boolean activeMember = companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(
                companyId,
                userId,
                STATUS_ACTIVE
        );

        if (!activeMember) {
            throw ApiException.forbidden(
                    ApiErrorCode.NOT_A_MEMBER,
                    "Пользователь не состоит в компании"
            );
        }
    }

    private String normalizeQuestion(AiAskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "question обязателен"
            );
        }

        String question = request.question().trim();

        if (question.length() > 5000) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "question не должен превышать 5000 символов"
            );
        }

        return question;
    }
}