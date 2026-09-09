CREATE TABLE IF NOT EXISTS birchat.chat_read_states (
                                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    company_id UUID NOT NULL,
    chat_id UUID NOT NULL,
    user_id UUID NOT NULL,

    last_read_message_id UUID NULL,
    last_read_message_created_at TIMESTAMP NULL,

    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_chat_read_states_company
    FOREIGN KEY (company_id)
    REFERENCES birchat.companies(id),

    CONSTRAINT fk_chat_read_states_chat
    FOREIGN KEY (chat_id)
    REFERENCES birchat.chats(id),

    CONSTRAINT fk_chat_read_states_user
    FOREIGN KEY (user_id)
    REFERENCES birchat.users(id),

    CONSTRAINT fk_chat_read_states_last_message
    FOREIGN KEY (last_read_message_id)
    REFERENCES birchat.chat_messages(id),

    CONSTRAINT uk_chat_read_states_company_chat_user
    UNIQUE (company_id, chat_id, user_id)
    );

CREATE INDEX IF NOT EXISTS idx_chat_messages_company_chat_created_id_active
    ON birchat.chat_messages (company_id, chat_id, created_at, id)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_company_members_company_user_active
    ON birchat.company_members (company_id, user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_company_members_user_active
    ON birchat.company_members (user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_company_members_company_joined_active
    ON birchat.company_members (company_id, joined_at)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_chat_read_states_company_chat_user
    ON birchat.chat_read_states (company_id, chat_id, user_id);