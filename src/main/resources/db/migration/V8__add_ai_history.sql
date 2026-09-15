CREATE TABLE IF NOT EXISTS birchat.ai_threads (
                                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    company_id UUID NOT NULL,
    user_id UUID NOT NULL,

    title VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ai_threads_company
    FOREIGN KEY (company_id)
    REFERENCES birchat.companies (id),

    CONSTRAINT fk_ai_threads_user
    FOREIGN KEY (user_id)
    REFERENCES birchat.users (id)
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_ai_threads_company_user_default
    ON birchat.ai_threads (company_id, user_id)
    WHERE is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_ai_threads_company_user
    ON birchat.ai_threads (company_id, user_id);


CREATE TABLE IF NOT EXISTS birchat.ai_messages (
                                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    thread_id UUID NOT NULL,
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,

    role VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    model VARCHAR(100),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ai_messages_thread
    FOREIGN KEY (thread_id)
    REFERENCES birchat.ai_threads (id),

    CONSTRAINT fk_ai_messages_company
    FOREIGN KEY (company_id)
    REFERENCES birchat.companies (id),

    CONSTRAINT fk_ai_messages_user
    FOREIGN KEY (user_id)
    REFERENCES birchat.users (id)
    );

CREATE INDEX IF NOT EXISTS idx_ai_messages_thread_created_at
    ON birchat.ai_messages (thread_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_ai_messages_company_user_created_at
    ON birchat.ai_messages (company_id, user_id, created_at DESC);