-- Индексы для ночной чистки старых данных (DataCleanupService).

CREATE INDEX IF NOT EXISTS idx_auth_codes_created_at
    ON birchat.auth_codes (created_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked_at
    ON birchat.refresh_tokens (revoked_at)
    WHERE revoked_at IS NOT NULL;
