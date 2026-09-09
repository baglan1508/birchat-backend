CREATE TABLE IF NOT EXISTS birchat.chat_attachments (
                                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    company_id UUID NOT NULL,
    chat_id UUID NOT NULL,
    message_id UUID NOT NULL,
    file_id UUID NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_chat_attachments_company
    FOREIGN KEY (company_id)
    REFERENCES birchat.companies(id),

    CONSTRAINT fk_chat_attachments_chat
    FOREIGN KEY (chat_id)
    REFERENCES birchat.chats(id),

    CONSTRAINT fk_chat_attachments_message
    FOREIGN KEY (message_id)
    REFERENCES birchat.chat_messages(id),

    CONSTRAINT fk_chat_attachments_file
    FOREIGN KEY (file_id)
    REFERENCES birchat.company_files(id),

    CONSTRAINT uk_chat_attachments_message_file
    UNIQUE (message_id, file_id)
    );

CREATE INDEX IF NOT EXISTS idx_chat_attachments_message
    ON birchat.chat_attachments (message_id);

CREATE INDEX IF NOT EXISTS idx_chat_attachments_file
    ON birchat.chat_attachments (file_id);

CREATE INDEX IF NOT EXISTS idx_chat_attachments_company_chat
    ON birchat.chat_attachments (company_id, chat_id);