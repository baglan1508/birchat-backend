package kz.birchat.api.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Удаление старых служебных строк порциями: каждый вызов удаляет не больше limit строк
 * отдельным коротким запросом, чтобы не держать долгие блокировки таблиц.
 */
@Repository
@RequiredArgsConstructor
public class DataCleanupRepository {

    private final JdbcTemplate jdbcTemplate;

    public int deleteMinuteQuotaWindowsBefore(OffsetDateTime cutoff, int limit) {
        return jdbcTemplate.update("""
                DELETE FROM birchat.ai_request_quota
                WHERE ctid IN (
                    SELECT ctid FROM birchat.ai_request_quota
                    WHERE scope = 'USER_MINUTE'
                      AND window_start < ?
                    LIMIT ?
                )
                """, cutoff, limit);
    }

    public int deleteDayQuotaWindowsBefore(OffsetDateTime cutoff, int limit) {
        return jdbcTemplate.update("""
                DELETE FROM birchat.ai_request_quota
                WHERE ctid IN (
                    SELECT ctid FROM birchat.ai_request_quota
                    WHERE scope IN ('USER_DAY', 'COMPANY_DAY')
                      AND window_start < ?
                    LIMIT ?
                )
                """, cutoff, limit);
    }

    public int deleteExpiredRefreshTokensBefore(LocalDateTime cutoff, int limit) {
        return jdbcTemplate.update("""
                DELETE FROM birchat.refresh_tokens
                WHERE id IN (
                    SELECT id FROM birchat.refresh_tokens
                    WHERE expires_at < ?
                    LIMIT ?
                )
                """, cutoff, limit);
    }

    public int deleteRevokedRefreshTokensBefore(LocalDateTime cutoff, int limit) {
        return jdbcTemplate.update("""
                DELETE FROM birchat.refresh_tokens
                WHERE id IN (
                    SELECT id FROM birchat.refresh_tokens
                    WHERE revoked_at IS NOT NULL
                      AND revoked_at < ?
                    LIMIT ?
                )
                """, cutoff, limit);
    }

    public int deleteAuthCodesBefore(LocalDateTime cutoff, int limit) {
        return jdbcTemplate.update("""
                DELETE FROM birchat.auth_codes
                WHERE id IN (
                    SELECT id FROM birchat.auth_codes
                    WHERE created_at < ?
                    LIMIT ?
                )
                """, cutoff, limit);
    }
}
