package kz.birchat.api.service;

import jakarta.persistence.EntityManager;
import kz.birchat.api.ai.AiProvider;
import kz.birchat.api.ai.AiProviderMessage;
import kz.birchat.api.ai.AiProviderRequest;
import kz.birchat.api.ai.AiProviderResolver;
import kz.birchat.api.ai.AiProviderResponse;
import kz.birchat.api.ai.AiSummaryProviderRequest;
import kz.birchat.api.ai.AiSummaryProviderResponse;
import kz.birchat.api.config.AiProperties;
import kz.birchat.api.dto.AiAskRequest;
import kz.birchat.api.dto.AiAskResponse;
import kz.birchat.api.dto.AiDirectorSummaryResponse;
import kz.birchat.api.dto.AiHistoryMessageResponse;
import kz.birchat.api.dto.AiHistoryResponse;
import kz.birchat.api.entity.AiCompanySummaryEntity;
import kz.birchat.api.entity.AiMessageEntity;
import kz.birchat.api.entity.AiThreadEntity;
import kz.birchat.api.entity.ChatMessageEntity;
import kz.birchat.api.entity.CompanyEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.AiMessageRole;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.AiCompanySummaryRepository;
import kz.birchat.api.repository.AiMessageRepository;
import kz.birchat.api.repository.AiThreadRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 100;

    private static final int AI_CHAT_CONTEXT_LIMIT = 30;
    private static final int AI_CONTEXT_MESSAGE_MAX_LENGTH = 500;

    private static final int DEFAULT_SUMMARY_INTERVAL_HOURS = 3;
    private static final int DEFAULT_SUMMARY_MAX_MESSAGES = 100;
    private static final int DEFAULT_SUMMARY_MAX_MESSAGE_LENGTH = 500;
    private static final String DEFAULT_SUMMARY_ZONE = "Asia/Almaty";

    private final CompanyMemberRepository companyMemberRepository;
    private final CompanyRepository companyRepository;
    private final AiThreadRepository aiThreadRepository;
    private final AiMessageRepository aiMessageRepository;
    private final AiCompanySummaryRepository aiCompanySummaryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiProviderResolver aiProviderResolver;
    private final AiProperties aiProperties;
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

        List<AiProviderMessage> contextMessages = loadRecentGeneralChatContext(companyId);

        AiProvider provider = aiProviderResolver.resolve();

        AiProviderResponse providerResponse = provider.ask(
                new AiProviderRequest(
                        companyId,
                        userId,
                        question,
                        contextMessages
                )
        );

        String answer = providerResponse.answer();
        String model = providerResponse.model();

        LocalDateTime answerCreatedAt = TimeUtils.utcNow();

        AiMessageEntity assistantMessage = saveMessage(
                thread,
                AiMessageRole.ASSISTANT,
                answer,
                model,
                answerCreatedAt
        );

        thread.setUpdatedAt(answerCreatedAt);
        aiThreadRepository.save(thread);

        return new AiAskResponse(
                thread.getId(),
                assistantMessage.getId(),
                answer,
                model,
                TimeUtils.toUtcOffset(answerCreatedAt)
        );
    }

    @Transactional
    public AiDirectorSummaryResponse getTodayDirectorSummary(
            UUID companyId,
            UUID userId
    ) {
        checkActiveMember(companyId, userId);

        AiCompanySummaryEntity summary = getFreshOrGenerateCompanySummary(companyId);

        return toDirectorSummaryResponse(summary);
    }

    @Transactional
    public void refreshCompanySummaries() {
        List<CompanyEntity> companies = companyRepository.findByStatus(STATUS_ACTIVE);

        log.info("AI company summary refresh started. companiesCount={}", companies.size());

        for (CompanyEntity company : companies) {
            if (company == null || company.getId() == null) {
                continue;
            }

            try {
                generateAndSaveCompanySummary(
                        company.getId(),
                        TimeUtils.utcNow()
                );
            } catch (Exception ex) {
                log.error(
                        "Failed to refresh AI company summary. companyId={}",
                        company.getId(),
                        ex
                );
            }
        }

        log.info("AI company summary refresh finished");
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

    private AiCompanySummaryEntity getFreshOrGenerateCompanySummary(UUID companyId) {
        LocalDateTime now = TimeUtils.utcNow();
        LocalDateTime freshAfter = now.minusHours(summaryIntervalHours());

        return aiCompanySummaryRepository
                .findFirstByCompanyIdAndGeneratedAtAfterOrderByGeneratedAtDesc(
                        companyId,
                        freshAfter
                )
                .filter(summary -> !hasNewMessagesAfterSummary(companyId, summary))
                .orElseGet(() -> {
                    try {
                        return generateAndSaveCompanySummary(companyId, now);
                    } catch (Exception ex) {
                        log.error(
                                "Failed to generate fresh AI summary. Trying to return latest existing summary. companyId={}",
                                companyId,
                                ex
                        );

                        return aiCompanySummaryRepository
                                .findFirstByCompanyIdOrderByGeneratedAtDesc(companyId)
                                .orElseThrow(() -> new ApiException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        ApiErrorCode.INTERNAL_ERROR,
                                        "Не удалось сформировать AI-сводку"
                                ));
                    }
                });
    }

    private boolean hasNewMessagesAfterSummary(
            UUID companyId,
            AiCompanySummaryEntity summary
    ) {
        if (summary == null || summary.getPeriodEnd() == null) {
            return true;
        }

        Long count = chatMessageRepository.countGeneralChatMessagesAfterForAiSummary(
                companyId,
                summary.getPeriodEnd()
        );

        return count != null && count > 0;
    }

    private AiCompanySummaryEntity generateAndSaveCompanySummary(
            UUID companyId,
            LocalDateTime now
    ) {
        LocalDateTime periodStart = buildTodayPeriodStartUtc(now);
        LocalDateTime periodEnd = now;

        List<AiProviderMessage> contextMessages = loadSummaryChatContext(
                companyId,
                periodStart,
                periodEnd
        );

        AiProvider provider = aiProviderResolver.resolve();

        AiSummaryProviderResponse providerResponse = provider.summarize(
                new AiSummaryProviderRequest(
                        companyId,
                        periodStart,
                        periodEnd,
                        contextMessages
                )
        );

        CompanyEntity companyRef = entityManager.getReference(
                CompanyEntity.class,
                companyId
        );

        AiCompanySummaryEntity entity = new AiCompanySummaryEntity();
        entity.setId(UUID.randomUUID());
        entity.setCompany(companyRef);
        entity.setTitle(normalizeSummaryTitle(providerResponse.title()));
        entity.setSummary(normalizeSummaryText(providerResponse.summary()));
        entity.setItemsText(joinSummaryItems(providerResponse.items()));
        entity.setModel(providerResponse.model());
        entity.setPeriodStart(periodStart);
        entity.setPeriodEnd(periodEnd);
        entity.setGeneratedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return aiCompanySummaryRepository.save(entity);
    }

    private List<AiProviderMessage> loadSummaryChatContext(
            UUID companyId,
            LocalDateTime periodStart,
            LocalDateTime periodEnd
    ) {
        return chatMessageRepository
                .findGeneralChatMessagesForAiSummary(
                        companyId,
                        periodStart,
                        periodEnd,
                        PageRequest.of(0, summaryMaxMessages())
                )
                .stream()
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt)
                        .thenComparing(ChatMessageEntity::getId))
                .map(message -> toProviderMessage(
                        message,
                        summaryMaxMessageLength()
                ))
                .toList();
    }

    private List<AiProviderMessage> loadRecentGeneralChatContext(UUID companyId) {
        return chatMessageRepository
                .findLatestGeneralChatMessagesForAiContext(
                        companyId,
                        PageRequest.of(0, AI_CHAT_CONTEXT_LIMIT)
                )
                .stream()
                .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt)
                        .thenComparing(ChatMessageEntity::getId))
                .map(message -> toProviderMessage(
                        message,
                        AI_CONTEXT_MESSAGE_MAX_LENGTH
                ))
                .toList();
    }

    private AiProviderMessage toProviderMessage(
            ChatMessageEntity message,
            int maxLength
    ) {
        return new AiProviderMessage(
                "GENERAL_CHAT",
                buildAuthor(message),
                truncateForAiContext(message.getContent(), maxLength),
                message.getCreatedAt() == null
                        ? null
                        : TimeUtils.toUtcOffset(message.getCreatedAt()).toString()
        );
    }

    private String buildAuthor(ChatMessageEntity message) {
        if (message.getUser() == null) {
            return "Пользователь";
        }

        UserEntity user = message.getUser();

        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }

        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }

        if (user.getInitials() != null && !user.getInitials().isBlank()) {
            return user.getInitials().trim();
        }

        if (user.getId() != null) {
            return "userId=" + user.getId();
        }

        return "Пользователь";
    }

    private String truncateForAiContext(
            String value,
            int maxLength
    ) {
        String normalized = value.trim();

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength) + "...";
    }

    private AiDirectorSummaryResponse toDirectorSummaryResponse(AiCompanySummaryEntity summary) {
        return new AiDirectorSummaryResponse(
                summary.getTitle(),
                summary.getSummary(),
                splitSummaryItems(summary.getItemsText()),
                TimeUtils.toUtcOffset(summary.getGeneratedAt())
        );
    }

    private String normalizeSummaryTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Сводка за сегодня";
        }

        return title.trim();
    }

    private String normalizeSummaryText(String summary) {
        if (summary == null || summary.isBlank()) {
            return "За выбранный период недостаточно данных для сводки.";
        }

        return summary.trim();
    }

    private String joinSummaryItems(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        return items
                .stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .collect(Collectors.joining("\n"));
    }

    private List<String> splitSummaryItems(String itemsText) {
        if (itemsText == null || itemsText.isBlank()) {
            return List.of();
        }

        return Arrays.stream(itemsText.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private LocalDateTime buildTodayPeriodStartUtc(LocalDateTime nowUtc) {
        ZoneId zone = resolveSummaryZone();

        ZonedDateTime nowInSummaryZone = nowUtc
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(zone);

        ZonedDateTime startOfDayInSummaryZone = nowInSummaryZone
                .toLocalDate()
                .atStartOfDay(zone);

        return startOfDayInSummaryZone
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private ZoneId resolveSummaryZone() {
        String zone = aiProperties.getSummary() == null
                ? DEFAULT_SUMMARY_ZONE
                : aiProperties.getSummary().getZone();

        if (zone == null || zone.isBlank()) {
            return ZoneId.of(DEFAULT_SUMMARY_ZONE);
        }

        try {
            return ZoneId.of(zone.trim());
        } catch (Exception ex) {
            log.warn("Invalid ai.summary.zone={}, fallback={}", zone, DEFAULT_SUMMARY_ZONE);
            return ZoneId.of(DEFAULT_SUMMARY_ZONE);
        }
    }

    private int summaryIntervalHours() {
        if (aiProperties.getSummary() == null
                || aiProperties.getSummary().getIntervalHours() == null
                || aiProperties.getSummary().getIntervalHours() < 1) {
            return DEFAULT_SUMMARY_INTERVAL_HOURS;
        }

        return aiProperties.getSummary().getIntervalHours();
    }

    private int summaryMaxMessages() {
        if (aiProperties.getSummary() == null
                || aiProperties.getSummary().getMaxMessages() == null
                || aiProperties.getSummary().getMaxMessages() < 1) {
            return DEFAULT_SUMMARY_MAX_MESSAGES;
        }

        return aiProperties.getSummary().getMaxMessages();
    }

    private int summaryMaxMessageLength() {
        if (aiProperties.getSummary() == null
                || aiProperties.getSummary().getMaxMessageLength() == null
                || aiProperties.getSummary().getMaxMessageLength() < 1) {
            return DEFAULT_SUMMARY_MAX_MESSAGE_LENGTH;
        }

        return aiProperties.getSummary().getMaxMessageLength();
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