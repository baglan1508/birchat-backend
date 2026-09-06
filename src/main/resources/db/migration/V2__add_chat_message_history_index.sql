CREATE INDEX IF NOT EXISTS idx_chat_messages_company_chat_created_active
    ON birchat.chat_messages (company_id, chat_id, created_at)
    WHERE is_deleted = false;