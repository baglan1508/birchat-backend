CREATE TABLE birchat.refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES birchat.users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_refresh_tokens_user ON birchat.refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expiry ON birchat.refresh_tokens(expires_at);
