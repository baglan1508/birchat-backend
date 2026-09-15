package kz.birchat.api.service;

import jakarta.persistence.EntityManager;
import kz.birchat.api.dto.AiAskRequest;
import kz.birchat.api.dto.AiAskResponse;
import kz.birchat.api.dto.AiDirectorSummaryResponse;
import kz.birchat.api.dto.AiHistoryMessageResponse;
import kz.birchat.api.dto.AiHistoryResponse;
import kz.birchat.api.entity.AiMessageEntity;
import kz.birchat.api.entity.AiThreadEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.AiMessageRole;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.AiMessageRepository;
import kz.birchat.api.repository.AiThreadRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MOCK_MODEL = "mock";
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 100;

    private final CompanyMemberRepository companyMemberRepository;
    private final AiThreadRepository aiThreadRepository;
    private final AiMessageRepository aiMessageRepository;
    private final EntityManager entityManager;

    @Transactional
    public AiAskResponse ask(
            UUID companyId,
            UUID userId,
            AiAskRequest request
    ) {
        checkActiveMember(companyId, userId);

        String question = normalizeQuestion(request);
        LocalDateTime now = TimeUtils.utcNow();

        AiThreadEntity thread = getOrCreateDefaultThread(
                companyId,
                userId,
                question,
                now
        );

        saveMessage(
                thread,
                AiMessageRole.USER,
                question,
                null,
                now
        );

        String answer = buildMockAnswer(question);
        LocalDateTime answerCreatedAt = TimeUtils.utcNow();

        AiMessageEntity assistantMessage = saveMessage(
                thread,
                AiMessageRole.ASSISTANT,
                answer,
                MOCK_MODEL,
                answerCreatedAt
        );

        thread.setUpdatedAt(answerCreatedAt);
        aiThreadRepository.save(thread);

        return new AiAskResponse(
                thread.getId(),
                assistantMessage.getId(),
                answer,
                MOCK_MODEL,
                TimeUtils.toUtcOffset(answerCreatedAt)
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

    @Transactional(readOnly = true)
    public AiHistoryResponse getHistory(
            UUID companyId,
            UUID userId,
            Integer limit
    ) {
        checkActiveMember(companyId, userId);

        int safeLimit = normalizeHistoryLimit(limit);

        return aiThreadRepository.findDefaultThread(companyId, userId)
                .map(thread -> {
                    List<AiHistoryMessageResponse> messages = aiMessageRepository
                            .findLatestByThreadId(
                                    thread.getId(),
                                    PageRequest.of(0, safeLimit)
                            )
                            .stream()
                            .sorted(Comparator.comparing(AiMessageEntity::getCreatedAt)
                                    .thenComparing(AiMessageEntity::getId))
                            .map(this::toHistoryMessageResponse)
                            .toList();

                    return new AiHistoryResponse(
                            thread.getId(),
                            messages
                    );
                })
                .orElseGet(() -> new AiHistoryResponse(
                        null,
                        List.of()
                ));
    }

    private AiThreadEntity getOrCreateDefaultThread(
            UUID companyId,
            UUID userId,
            String question,
            LocalDateTime now
    ) {
        return aiThreadRepository.findDefaultThread(companyId, userId)
                .orElseGet(() -> createDefaultThread(
                        companyId,
                        userId,
                        question,
                        now
                ));
    }

    private AiThreadEntity createDefaultThread(
            UUID companyId,
            UUID userId,
            String question,
            LocalDateTime now
    ) {
        CompanyEntity companyRef = entityManager.getReference(
                CompanyEntity.class,
                companyId
        );

        UserEntity userRef = entityManager.getReference(
                UserEntity.class,
                userId
        );

        AiThreadEntity thread = new AiThreadEntity();
        thread.setId(UUID.randomUUID());
        thread.setCompany(companyRef);
        thread.setUser(userRef);
        thread.setTitle(buildThreadTitle(question));
        thread.setDefaultThread(true);
        thread.setCreatedAt(now);
        thread.setUpdatedAt(now);

        return aiThreadRepository.save(thread);
    }

    private AiMessageEntity saveMessage(
            AiThreadEntity thread,
            AiMessageRole role,
            String content,
            String model,
            LocalDateTime createdAt
    ) {
        AiMessageEntity message = new AiMessageEntity();
        message.setId(UUID.randomUUID());
        message.setThread(thread);
        message.setCompany(thread.getCompany());
        message.setUser(thread.getUser());
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setCreatedAt(createdAt);

        return aiMessageRepository.save(message);
    }

    private AiHistoryMessageResponse toHistoryMessageResponse(AiMessageEntity message) {
        return new AiHistoryMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getModel(),
                TimeUtils.toUtcOffset(message.getCreatedAt())
        );
    }

    private String buildMockAnswer(String question) {
        return """
                AI mock: я пока работаю в тестовом режиме.
                Позже здесь будет ответ на основе сообщений, файлов и памяти компании.

                Ваш вопрос: %s
                """.formatted(question);
    }

    private String buildThreadTitle(String question) {
        String normalized = question.trim();

        if (normalized.length() <= 60) {
            return normalized;
        }

        return normalized.substring(0, 60) + "...";
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

    private int normalizeHistoryLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_HISTORY_LIMIT;
        }

        if (limit < 1) {
            throw ApiException.badRequest(
                    ApiErrorCode.VALIDATION,
                    "limit должен быть больше 0"
            );
        }

        return Math.min(limit, MAX_HISTORY_LIMIT);
    }
}