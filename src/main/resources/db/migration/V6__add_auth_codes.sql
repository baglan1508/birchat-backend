CREATE TABLE IF NOT EXISTS birchat.auth_codes (
                                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    phone VARCHAR(20) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,

    attempts_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,

    expires_at TIMESTAMP NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,

    provider VARCHAR(50),
    provider_message_id VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_auth_codes_phone_created_at
    ON birchat.auth_codes (phone, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_auth_codes_phone_consumed_created_at
    ON birchat.auth_codes (phone, consumed, created_at DESC);