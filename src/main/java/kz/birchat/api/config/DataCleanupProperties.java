package kz.birchat.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Сроки хранения служебных данных. Всё, что старше, удаляется ночной задачей.
 * Пустые значения — значения по умолчанию из DataCleanupService.
 */
@ConfigurationProperties(prefix = "cleanup")
public record DataCleanupProperties(
        Boolean enabled,
        Duration quotaMinuteRetention,
        Duration quotaDayRetention,
        Duration refreshTokenRetention,
        Duration authCodeRetention,
        Duration pendingFileRetention,
        Duration aiHistoryRetention,
        Integer batchSize
) {
}
