package kz.birchat.api.service;

import kz.birchat.api.ai.AiMediaResult;
import kz.birchat.api.ai.AiProviderResolver;
import kz.birchat.api.config.AiProperties;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Фоновая обработка вложений для AI: модель описывает фото (что изображено, главный текст),
 * описание сохраняется в company_files.ai_text и попадает в контекст сводок и вопросов к AI.
 *
 * Очередь — сами файлы со статусом ai_status = PENDING. Обработка идёт по одному файлу,
 * транзакции короткие: соединение с базой не занято, пока модель смотрит фото.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAnalysisService {

    public static final String AI_PENDING = "PENDING";
    public static final String AI_DONE = "DONE";
    public static final String AI_FAILED = "FAILED";
    public static final String AI_SKIPPED = "SKIPPED";

    static final int MAX_ATTEMPTS = 3;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final int SIGNED_URL_TTL_SECONDS = 10 * 60;
    private static final int MAX_ERROR_LENGTH = 500;

    /** Форматы, которые принимает модель с «зрением». */
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final CompanyFileRepository companyFileRepository;
    private final SupabaseStorageService storageService;
    private final AiProviderResolver aiProviderResolver;
    private final AiProperties aiProperties;
    private final PlatformTransactionManager transactionManager;

    /** Поставить файл в очередь, если AI умеет его разбирать (сейчас — фото). */
    public static void markForAnalysisIfSupported(CompanyFileEntity file) {
        if (file.getAiStatus() == null && isImage(file.getContentType())) {
            file.setAiStatus(AI_PENDING);
        }
    }

    public boolean isEnabled() {
        return aiProperties.getMedia() == null || aiProperties.getMedia().isEnabled();
    }

    /** Один проход очереди. Возвращает, сколько файлов обработано (любым итогом). */
    public int processPending() {
        int batchSize = aiProperties.getMedia() == null ? 5 : Math.max(1, aiProperties.getMedia().getBatchSize());

        List<UUID> ids = readTx().execute(status -> companyFileRepository
                .findPendingForAi(TimeUtils.utcNow(), PageRequest.of(0, batchSize))
                .stream()
                .map(CompanyFileEntity::getId)
                .toList());

        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        for (UUID id : ids) {
            process(id);
        }

        log.info("File AI analysis pass finished. files={}", ids.size());
        return ids.size();
    }

    void process(UUID fileId) {
        CompanyFileEntity file = readTx().execute(status -> companyFileRepository
                .findById(fileId)
                .filter(item -> AI_PENDING.equals(item.getAiStatus()))
                .orElse(null));

        if (file == null) {
            return;
        }

        String skipReason = skipReason(file);

        if (skipReason != null) {
            finish(fileId, AI_SKIPPED, null, null, skipReason);
            return;
        }

        try {
            String url = storageService
                    .createSignedUrls(List.of(file.getStorageKey()), SIGNED_URL_TTL_SECONDS)
                    .get(file.getStorageKey());

            if (url == null || url.isBlank()) {
                throw new IllegalStateException("Хранилище не выдало ссылку на файл");
            }

            // Вызов модели — вне транзакции.
            AiMediaResult result = aiProviderResolver.resolve().describeImage(url);

            if (result == null || result.text() == null || result.text().isBlank()) {
                throw new IllegalStateException("Модель вернула пустое описание");
            }

            finish(fileId, AI_DONE, result.text().trim(), result.model(), null);
        } catch (Exception ex) {
            retryOrFail(fileId, ex);
        }
    }

    static String skipReason(CompanyFileEntity file) {
        String contentType = normalize(file.getContentType());

        if (!SUPPORTED_IMAGE_TYPES.contains(contentType)) {
            return "Формат не поддерживается моделью: " + contentType;
        }

        if (file.getFileSize() != null && file.getFileSize() > MAX_IMAGE_BYTES) {
            return "Фото больше 5 МБ";
        }

        return null;
    }

    private void finish(UUID fileId, String aiStatus, String text, String model, String error) {
        tx().executeWithoutResult(status -> companyFileRepository.findById(fileId).ifPresent(file -> {
            LocalDateTime now = TimeUtils.utcNow();

            file.setAiStatus(aiStatus);
            file.setAiText(text);
            file.setAiModel(model);
            file.setAiError(truncate(error));
            file.setAiNextAttemptAt(null);
            file.setAiProcessedAt(now);
            companyFileRepository.save(file);
        }));
    }

    private void retryOrFail(UUID fileId, Exception ex) {
        tx().executeWithoutResult(status -> companyFileRepository.findById(fileId).ifPresent(file -> {
            LocalDateTime now = TimeUtils.utcNow();
            int attempts = file.getAiAttempts() + 1;

            file.setAiAttempts(attempts);
            file.setAiError(truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage()));

            if (attempts >= MAX_ATTEMPTS) {
                file.setAiStatus(AI_FAILED);
                file.setAiNextAttemptAt(null);
                file.setAiProcessedAt(now);
                log.error("File AI analysis failed after {} attempts. fileId={}", attempts, fileId, ex);
            } else {
                // Повтор через 5, затем 10 минут.
                file.setAiNextAttemptAt(now.plusMinutes(5L * attempts));
                log.warn("File AI analysis attempt {} failed, will retry. fileId={} error={}",
                        attempts, fileId, ex.getMessage());
            }

            companyFileRepository.save(file);
        }));
    }

    private static boolean isImage(String contentType) {
        return normalize(contentType).startsWith("image/");
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }

        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_ERROR_LENGTH);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private TransactionTemplate readTx() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }
}
