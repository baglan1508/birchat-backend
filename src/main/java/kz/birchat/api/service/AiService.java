package kz.birchat.api.service;

import jakarta.persistence.EntityManager;
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
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.entity.UserEntity;
import kz.birchat.api.enums.AiMessageRole;
import kz.birchat.api.enums.ChatMessageType;
import kz.birchat.api.exception.ApiErrorCode;
import kz.birchat.api.exception.ApiException;
import kz.birchat.api.repository.AiCompanySummaryRepository;
import kz.birchat.api.repository.AiMessageRepository;
import kz.birchat.api.repository.AiThreadRepository;
import kz.birchat.api.repository.ChatAttachmentRepository;
import kz.birchat.api.repository.ChatMessageRepository;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.CompanyMemberRepository;
import kz.birchat.api.repository.CompanyRepository;
import kz.birchat.api.entity.CompanyMemberEntity;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final int DEFAULT_HISTORY_LIMIT = 50;
    public static final String HISTORY_ORDER_OLDEST = "oldest";
    public static final String HISTORY_ORDER_NEWEST = "newest";
    private static final int MAX_HISTORY_LIMIT = 100;

    private static final int AI_CHAT_CONTEXT_LIMIT = 30;
    private static final int AI_CONTEXT_MESSAGE_MAX_LENGTH = 500;
    /** Текст чека или PDF в вопросе к AI — целиком (в сводке — короче, см. summaryMaxMessageLength). */
    private static final int AI_CONTEXT_MEDIA_MAX_LENGTH = 6000;

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
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final CompanyFileRepository companyFileRepository;
    private final AiProviderResolver aiProviderResolver;
    private final AiProperties aiProperties;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    private record AskContext(String question, List<AiProviderMessage> chatContext) {
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private TransactionTemplate readTx() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    /**
     * Вопрос к AI. Транзакции короткие: соединение с базой не занято, пока ждём ответ модели
     * (до 60 секунд). Вопрос и ответ сохраняются вместе только после успешного ответа.
     */
    public AiAskResponse ask(
            UUID companyId,
            UUID userId,
            AiAskRequest request
    ) {
        LocalDateTime askedAt = TimeUtils.utcNow();

        // 1. Короткое чтение: права и контекст общего чата.
        AskContext context = readTx().execute(status -> {
            checkActiveMember(companyId, userId);
            String question = normalizeQuestion(request);
            return new AskContext(question, loadRecentGeneralChatContext(companyId));
        });

        // 2. Вызов модели — вне транзакции.
        AiProviderResponse providerResponse = aiProviderResolver.resolve().ask(
                new AiProviderRequest(
                        companyId,
                        userId,
                        context.question(),
                        context.chatContext()
                )
        );

        // 3. Короткая запись: вопрос и ответ.
        return tx().execute(status -> {
            AiThreadEntity thread = getOrCreateDefaultThread(
                    companyId,
                    userId,
                    context.question(),
                    askedAt
            );

            saveMessage(thread, AiMessageRole.USER, context.question(), null, askedAt);

            LocalDateTime answerCreatedAt = TimeUtils.utcNow();

            AiMessageEntity assistantMessage = saveMessage(
                    thread,
                    AiMessageRole.ASSISTANT,
                    providerResponse.answer(),
                    providerResponse.model(),
                    answerCreatedAt
            );

            thread.setUpdatedAt(answerCreatedAt);
            aiThreadRepository.save(thread);

            return new AiAskResponse(
                    thread.getId(),
                    assistantMessage.getId(),
                    providerResponse.answer(),
                    providerResponse.model(),
                    TimeUtils.toUtcOffset(answerCreatedAt)
            );
        });
    }

    public AiDirectorSummaryResponse getTodayDirectorSummary(
            UUID companyId,
            UUID userId
    ) {
        AiDirectorSummaryResponse fresh = readTx().execute(status -> {
            requireDirector(companyId, userId);

            return findFreshSummary(companyId)
                    .map(this::toDirectorSummaryResponse)
                    .orElse(null);
        });

        if (fresh != null) {
            return fresh;
        }

        try {
            return toDirectorSummaryResponse(
                    generateAndSaveCompanySummary(companyId, TimeUtils.utcNow())
            );
        } catch (Exception ex) {
            log.error(
                    "Failed to generate fresh AI summary. Trying to return latest existing summary. companyId={}",
                    companyId,
                    ex
            );

            return readTx().execute(status -> aiCompanySummaryRepository
                    .findFirstByCompanyIdOrderByGeneratedAtDesc(companyId)
                    .map(this::toDirectorSummaryResponse)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            ApiErrorCode.INTERNAL_ERROR,
                            "Не удалось сформировать AI-сводку"
                    )));
        }
    }

    /**
     * Фоновое обновление сводок. Каждая компания — отдельно: ошибка одной не откатывает остальные,
     * и соединение с базой не держится, пока модель формирует сводку.
     */
    public void refreshCompanySummaries() {
        List<UUID> companyIds = readTx().execute(status -> companyRepository
                .findByStatus(STATUS_ACTIVE)
                .stream()
                .filter(company -> company != null && company.getId() != null)
                .map(CompanyEntity::getId)
                .toList());

        log.info("AI company summary refresh started. companiesCount={}", companyIds.size());

        for (UUID companyId : companyIds) {
            try {
                generateAndSaveCompanySummary(companyId, TimeUtils.utcNow());
            } catch (Exception ex) {
                log.error(
                        "Failed to refresh AI company summary. companyId={}",
                        companyId,
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
        return getHistory(companyId, userId, limit, HISTORY_ORDER_OLDEST);
    }

    /**
     * Диалог с AI на экране.
     *
     * По умолчанию — только сегодняшний (с полуночи по Алматы): вчерашние ответы «что было сегодня»
     * больше не висят на экране. Старые вопросы удаляет ночная чистка.
     *
     * @param order oldest — от старых к новым (как в чате); newest — новые вопросы сверху,
     *              внутри каждого — вопрос, затем ответ
     */
    @Transactional(readOnly = true)
    public AiHistoryResponse getHistory(
            UUID companyId,
            UUID userId,
            Integer limit,
            String order
    ) {
        checkActiveMember(companyId, userId);

        int safeLimit = normalizeHistoryLimit(limit);
        boolean newestFirst = isNewestFirst(order);

        return aiThreadRepository.findDefaultThread(companyId, userId)
                .map(thread -> {
                    List<AiMessageEntity> ascending = loadHistory(thread.getId(), safeLimit)
                            .stream()
                            .sorted(Comparator.comparing(AiMessageEntity::getCreatedAt)
                                    .thenComparing(AiMessageEntity::getId))
                            .toList();

                    List<AiHistoryMessageResponse> messages = (newestFirst
                            ? newestExchangesFirst(ascending)
                            : ascending)
                            .stream()
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

    private List<AiMessageEntity> loadHistory(UUID threadId, int limit) {
        boolean dailyReset = aiProperties.getHistory() == null || aiProperties.getHistory().isDailyReset();

        if (!dailyReset) {
            return aiMessageRepository.findLatestByThreadId(threadId, PageRequest.of(0, limit));
        }

        return aiMessageRepository.findLatestByThreadIdSince(
                threadId,
                buildTodayPeriodStartUtc(TimeUtils.utcNow()),
                PageRequest.of(0, limit)
        );
    }

    private static boolean isNewestFirst(String order) {
        if (order == null || order.isBlank() || HISTORY_ORDER_OLDEST.equalsIgnoreCase(order.trim())) {
            return false;
        }

        if (HISTORY_ORDER_NEWEST.equalsIgnoreCase(order.trim())) {
            return true;
        }

        throw ApiException.badRequest(ApiErrorCode.VALIDATION, "order: oldest или newest");
    }

    /**
     * Новые вопросы сверху, но внутри каждого — сначала вопрос, потом ответ
     * (простой разворот списка поставил бы ответ над вопросом).
     */
    static List<AiMessageEntity> newestExchangesFirst(List<AiMessageEntity> ascending) {
        List<List<AiMessageEntity>> exchanges = new ArrayList<>();

        for (AiMessageEntity message : ascending) {
            if (exchanges.isEmpty() || message.getRole() == AiMessageRole.USER) {
                exchanges.add(new ArrayList<>());
            }

            exchanges.get(exchanges.size() - 1).add(message);
        }

        Collections.reverse(exchanges);

        return exchanges.stream()
                .flatMap(List::stream)
                .toList();
    }

    /** «Новый диалог»: удаляет вопросы и ответы пользователя в этой компании. */
    @Transactional
    public void clearHistory(UUID companyId, UUID userId) {
        checkActiveMember(companyId, userId);

        aiThreadRepository.findDefaultThread(companyId, userId)
                .ifPresent(thread -> aiMessageRepository.deleteByThreadId(thread.getId()));
    }

    private void requireDirector(UUID companyId, UUID userId) {
        CompanyMemberEntity member = companyMemberRepository
                .findByCompanyIdAndUserIdAndStatus(
                        companyId,
                        userId,
                        STATUS_ACTIVE
                )
                .orElseThrow(() -> ApiException.forbidden(
                        ApiErrorCode.NOT_A_MEMBER,
                        "Пользователь не состоит в компании"
                ));

        if (member.getRole() == null
                || !"DIRECTOR".equals(member.getRole().getCode())) {
            throw ApiException.forbidden(
                    ApiErrorCode.FORBIDDEN,
                    "Операция доступна только директору компании"
            );
        }
    }

    private java.util.Optional<AiCompanySummaryEntity> findFreshSummary(UUID companyId) {
        LocalDateTime freshAfter = TimeUtils.utcNow().minusHours(summaryIntervalHours());

        return aiCompanySummaryRepository
                .findFirstByCompanyIdAndGeneratedAtAfterOrderByGeneratedAtDesc(
                        companyId,
                        freshAfter
                )
                .filter(summary -> !hasNewMessagesAfterSummary(companyId, summary));
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

        if (count != null && count > 0) {
            return true;
        }

        // Описание фото приходит через минуту после отправки — тогда сводку стоит пересчитать.
        return summary.getGeneratedAt() != null
                && companyFileRepository.countAnalyzedAfter(companyId, summary.getGeneratedAt()) > 0;
    }

    private AiCompanySummaryEntity generateAndSaveCompanySummary(
            UUID companyId,
            LocalDateTime now
    ) {
        LocalDateTime periodStart = buildTodayPeriodStartUtc(now);
        LocalDateTime periodEnd = now;

        // 1. Короткое чтение сообщений за период.
        List<AiProviderMessage> contextMessages = readTx().execute(status -> loadSummaryChatContext(
                companyId,
                periodStart,
                periodEnd
        ));

        // 2. Вызов модели — вне транзакции.
        AiSummaryProviderResponse providerResponse = aiProviderResolver.resolve().summarize(
                new AiSummaryProviderRequest(
                        companyId,
                        periodStart,
                        periodEnd,
                        contextMessages
                )
        );

        // 3. Короткая запись сводки.
        return tx().execute(status -> saveSummary(companyId, providerResponse, periodStart, periodEnd, now));
    }

    private AiCompanySummaryEntity saveSummary(
            UUID companyId,
            AiSummaryProviderResponse providerResponse,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            LocalDateTime now
    ) {
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
        List<ChatMessageEntity> messages = chatMessageRepository
                .findGeneralChatMessagesForAiSummary(
                        companyId,
                        periodStart,
                        periodEnd,
                        PageRequest.of(0, summaryMaxMessages())
                )
                .stream()
                .filter(AiService::hasAiContent)
                .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt)
                        .thenComparing(ChatMessageEntity::getId))
                .toList();

        int maxLength = summaryMaxMessageLength();

        return toProviderMessages(messages, maxLength, maxLength * 2);
    }

    private List<AiProviderMessage> loadRecentGeneralChatContext(UUID companyId) {
        List<ChatMessageEntity> messages = chatMessageRepository
                .findLatestGeneralChatMessagesForAiContext(
                        companyId,
                        PageRequest.of(0, AI_CHAT_CONTEXT_LIMIT)
                )
                .stream()
                .filter(AiService::hasAiContent)
                .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt)
                        .thenComparing(ChatMessageEntity::getId))
                .toList();

        return toProviderMessages(messages, AI_CONTEXT_MESSAGE_MAX_LENGTH, AI_CONTEXT_MEDIA_MAX_LENGTH);
    }

    /**
     * Сообщения для AI вместе с тем, что модель поняла из вложений (описание фото).
     * Описания берутся одним запросом для всех сообщений с вложениями.
     */
    private List<AiProviderMessage> toProviderMessages(
            List<ChatMessageEntity> messages,
            int maxLength,
            int mediaMaxLength
    ) {
        Map<UUID, String> mediaTexts = loadMediaTexts(messages);

        return messages.stream()
                .map(message -> {
                    String mediaText = mediaTexts.get(message.getId());

                    return toProviderMessage(message, mediaText, mediaText == null ? maxLength : mediaMaxLength);
                })
                .toList();
    }

    private Map<UUID, String> loadMediaTexts(List<ChatMessageEntity> messages) {
        List<UUID> withAttachments = messages.stream()
                .filter(message -> attachmentLabel(message.getType()) != null)
                .map(ChatMessageEntity::getId)
                .toList();

        if (withAttachments.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ChatMessageType> types = messages.stream()
                .collect(Collectors.toMap(ChatMessageEntity::getId, ChatMessageEntity::getType, (a, b) -> a));

        return chatAttachmentRepository.findByMessageIds(withAttachments)
                .stream()
                .filter(attachment -> attachment.getFile() != null)
                .map(attachment -> Map.entry(
                        attachment.getMessage().getId(),
                        mediaText(types.get(attachment.getMessage().getId()), attachment.getFile())
                ))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.joining("; "))
                ));
    }

    /**
     * Что AI узнаёт о вложении: текст, который модель прочитала из фото или PDF,
     * а для документов ещё и имя файла — «договор аренды.docx» само по себе говорит о многом.
     */
    static String mediaText(ChatMessageType type, CompanyFileEntity file) {
        String aiText = FileAnalysisService.AI_DONE.equals(file.getAiStatus())
                && file.getAiText() != null
                && !file.getAiText().isBlank()
                ? file.getAiText().trim()
                : null;

        if (type != ChatMessageType.DOCUMENT || file.getOriginalFileName() == null
                || file.getOriginalFileName().isBlank()) {
            return aiText;
        }

        String name = "«" + file.getOriginalFileName().trim() + "»";

        return aiText == null ? name : name + " — " + aiText;
    }

    /**
     * Сообщение попадает в контекст AI, если в нём есть текст или вложение.
     * Фото, голосовое или документ без подписи раньше пропускались — AI не знал, что их отправили.
     */
    static boolean hasAiContent(ChatMessageEntity message) {
        boolean hasText = message.getContent() != null && !message.getContent().isBlank();

        return hasText || attachmentLabel(message.getType()) != null;
    }

    /** Текст сообщения для AI: метка вложения («[фото]») и подпись, если есть. */
    static String aiContent(ChatMessageEntity message) {
        return aiContent(message, null);
    }

    /**
     * С описанием вложения от модели: «[фото: накладная №15 …] подпись».
     */
    static String aiContent(ChatMessageEntity message, String mediaText) {
        String text = message.getContent() == null ? "" : message.getContent().trim();
        String label = attachmentLabel(message.getType());

        if (label == null) {
            return text;
        }

        if (mediaText != null && !mediaText.isBlank()) {
            label = label.substring(0, label.length() - 1) + ": " + mediaText.trim() + "]";
        }

        return text.isEmpty() ? label : label + " " + text;
    }

    private static String attachmentLabel(ChatMessageType type) {
        if (type == null) {
            return null;
        }

        return switch (type) {
            case IMAGE -> "[фото]";
            case VOICE -> "[голосовое сообщение]";
            case DOCUMENT -> "[документ]";
            default -> null;
        };
    }

    private AiProviderMessage toProviderMessage(
            ChatMessageEntity message,
            String mediaText,
            int maxLength
    ) {
        return new AiProviderMessage(
                "GENERAL_CHAT",
                buildAuthor(message),
                truncateForAiContext(aiContent(message, mediaText), maxLength),
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