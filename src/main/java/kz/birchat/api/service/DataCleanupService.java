package kz.birchat.api.service;

import kz.birchat.api.config.DataCleanupProperties;
import kz.birchat.api.entity.CompanyFileEntity;
import kz.birchat.api.repository.CompanyFileRepository;
import kz.birchat.api.repository.DataCleanupRepository;
import kz.birchat.api.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Ночная чистка служебных данных, которые иначе копятся бесконечно:
 * окна AI-квот, истёкшие/отозванные refresh-токены, старые коды входа.
 *
 * Пользовательские данные (сообщения, файлы, история AI, сводки) не трогаются.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCleanupService {

    static final Duration DEFAULT_QUOTA_MINUTE_RETENTION = Duration.ofDays(1);
    static final Duration DEFAULT_QUOTA_DAY_RETENTION = Duration.ofDays(30);
    static final Duration DEFAULT_REFRESH_TOKEN_RETENTION = Duration.ofDays(7);
    static final Duration DEFAULT_AUTH_CODE_RETENTION = Duration.ofDays(7);
    static final Duration DEFAULT_PENDING_FILE_RETENTION = Duration.ofDays(1);
    static final int PENDING_FILES_BATCH_SIZE = 100;
    static final int DEFAULT_BATCH_SIZE = 1000;

    /** Не больше 1000 порций за запуск на таблицу, чтобы задача гарантированно завершилась. */
    static final int MAX_BATCHES = 1000;

    private final DataCleanupRepository repository;
    private final DataCleanupProperties properties;
    private final CompanyFileRepository companyFileRepository;
    private final SupabaseStorageService storageService;

    public boolean isEnabled() {
        return properties == null || properties.enabled() == null || properties.enabled();
    }

    /**
     * @return сколько строк удалено по каждому типу данных; -1 — удаление завершилось ошибкой
     */
    public Map<String, Integer> cleanup() {
        LocalDateTime now = TimeUtils.utcNow();
        int batch = batchSize();

        Map<String, Integer> report = new LinkedHashMap<>();

        report.put("aiQuotaMinuteWindows", run("aiQuotaMinuteWindows", limit ->
                repository.deleteMinuteQuotaWindowsBefore(
                        now.minus(quotaMinuteRetention()).atOffset(ZoneOffset.UTC), limit), batch));

        report.put("aiQuotaDayWindows", run("aiQuotaDayWindows", limit ->
                repository.deleteDayQuotaWindowsBefore(
                        now.minus(quotaDayRetention()).atOffset(ZoneOffset.UTC), limit), batch));

        report.put("expiredRefreshTokens", run("expiredRefreshTokens", limit ->
                repository.deleteExpiredRefreshTokensBefore(now.minus(refreshTokenRetention()), limit), batch));

        report.put("revokedRefreshTokens", run("revokedRefreshTokens", limit ->
                repository.deleteRevokedRefreshTokensBefore(now.minus(refreshTokenRetention()), limit), batch));

        report.put("authCodes", run("authCodes", limit ->
                repository.deleteAuthCodesBefore(now.minus(authCodeRetention()), limit), batch));

        report.put("pendingFiles", run("pendingFiles", limit ->
                deleteStalePendingFiles(now.minus(pendingFileRetention()), limit), PENDING_FILES_BATCH_SIZE));

        log.info("Data cleanup finished: {}", report);

        return report;
    }

    /**
     * Удаляет порциями, пока порция не окажется неполной. Ошибка в одной таблице
     * не останавливает чистку остальных.
     */
    private int run(String name, IntUnaryOperator deleteBatch, int batch) {
        int total = 0;

        try {
            for (int i = 0; i < MAX_BATCHES; i++) {
                int deleted = deleteBatch.applyAsInt(batch);
                total += deleted;

                if (deleted < batch) {
                    return total;
                }
            }

            log.warn("Data cleanup {}: reached {} batches, the rest will be removed next run", name, MAX_BATCHES);
            return total;
        } catch (Exception ex) {
            log.error("Data cleanup {} failed after deleting {} rows", name, total, ex);
            return -1;
        }
    }

    /**
     * Недозагруженные файлы (ссылка выдана, загрузка не подтверждена): удаляем объект
     * из хранилища и запись. Если хранилище недоступно — запись остаётся до следующей ночи.
     */
    private int deleteStalePendingFiles(LocalDateTime cutoff, int limit) {
        List<CompanyFileEntity> files = companyFileRepository.findStalePending(cutoff, PageRequest.of(0, limit));

        if (files.isEmpty()) {
            return 0;
        }

        storageService.deleteObjects(files.stream().map(CompanyFileEntity::getStorageKey).toList());
        companyFileRepository.deleteAllInBatch(files);

        return files.size();
    }

    private Duration pendingFileRetention() {
        return orDefault(properties == null ? null : properties.pendingFileRetention(), DEFAULT_PENDING_FILE_RETENTION);
    }

    private Duration quotaMinuteRetention() {
        return orDefault(properties == null ? null : properties.quotaMinuteRetention(), DEFAULT_QUOTA_MINUTE_RETENTION);
    }

    private Duration quotaDayRetention() {
        return orDefault(properties == null ? null : properties.quotaDayRetention(), DEFAULT_QUOTA_DAY_RETENTION);
    }

    private Duration refreshTokenRetention() {
        return orDefault(properties == null ? null : properties.refreshTokenRetention(), DEFAULT_REFRESH_TOKEN_RETENTION);
    }

    private Duration authCodeRetention() {
        return orDefault(properties == null ? null : properties.authCodeRetention(), DEFAULT_AUTH_CODE_RETENTION);
    }

    private int batchSize() {
        Integer value = properties == null ? null : properties.batchSize();
        return value == null || value < 1 ? DEFAULT_BATCH_SIZE : value;
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
