-- Удаление сообщения у всех: когда удалено (чтобы приложение могло узнать об удалениях
-- с момента последней проверки) и кем.
ALTER TABLE birchat.chat_messages
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS deleted_by UUID NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_deleted_at
    ON birchat.chat_messages (chat_id, deleted_at)
    WHERE deleted_at IS NOT NULL;
