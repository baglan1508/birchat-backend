package kz.birchat.api.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AiQuotaCounterRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean incrementIfBelowLimit(
            String scope,
            String subjectKey,
            Instant windowStart,
            int limit
    ) {
        if (limit < 1) {
            throw new IllegalArgumentException(
                    "AI quota limit must be positive"
            );
        }

        String sql = """
                INSERT INTO birchat.ai_request_quota AS q
                    (scope, subject_key, window_start, request_count)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (scope, subject_key, window_start)
                DO UPDATE
                SET request_count = q.request_count + 1
                WHERE q.request_count < ?
                RETURNING request_count
                """;

        List<Integer> results = jdbcTemplate.queryForList(
                sql,
                Integer.class,
                scope,
                subjectKey,
                OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC),
                limit
        );

        return !results.isEmpty();
    }
}
