-- Устройства для push-уведомлений (APNs). Один токен — одно устройство; у пользователя их может быть несколько.
CREATE TABLE IF NOT EXISTS birchat.device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    platform VARCHAR(20) NOT NULL,
    token VARCHAR(512) NOT NULL,
    -- production — TestFlight и App Store; sandbox — сборки из Xcode
    environment VARCHAR(20) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'ru',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES birchat.users(id),
    CONSTRAINT uq_device_tokens_token UNIQUE (token),
    CONSTRAINT chk_device_tokens_environment CHECK (environment IN ('production', 'sandbox'))
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON birchat.device_tokens (user_id);
