-- v1.6, P1: личные чаты, пересылка полем, редактирование, упоминания.

-- 1. Личные чаты (type = 'DIRECT'). Названия у них нет; на пару людей в компании — один чат:
--    direct_key = "меньший userId:больший userId", уникален в компании.
ALTER TABLE birchat.chats ALTER COLUMN name DROP NOT NULL;

ALTER TABLE birchat.chats
    ADD COLUMN IF NOT EXISTS direct_key VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS uq_chats_company_direct_key
    ON birchat.chats (company_id, direct_key)
    WHERE direct_key IS NOT NULL;

-- 2. Пересылка: откуда переслано (ответ уже есть — reply_to_message_id из V1).
ALTER TABLE birchat.chat_messages
    ADD COLUMN IF NOT EXISTS forwarded_from_message_id UUID;

ALTER TABLE birchat.chat_messages
    DROP CONSTRAINT IF EXISTS fk_chat_messages_forwarded_from;

ALTER TABLE birchat.chat_messages
    ADD CONSTRAINT fk_chat_messages_forwarded_from
        FOREIGN KEY (forwarded_from_message_id) REFERENCES birchat.chat_messages(id);

-- 3. Редактирование: когда сообщение последний раз изменили (null — не редактировали).
--    Индекс — для опроса GET …/messages/updated?since=.
ALTER TABLE birchat.chat_messages
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_edited_at
    ON birchat.chat_messages (chat_id, edited_at)
    WHERE edited_at IS NOT NULL;

-- 4. Упоминания: кого упомянули в сообщении (только участники чата).
CREATE TABLE IF NOT EXISTS birchat.chat_message_mentions (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chat_message_mentions_message FOREIGN KEY (message_id) REFERENCES birchat.chat_messages(id),
    CONSTRAINT fk_chat_message_mentions_user FOREIGN KEY (user_id) REFERENCES birchat.users(id),
    CONSTRAINT uq_chat_message_mentions UNIQUE (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_mentions_user
    ON birchat.chat_message_mentions (user_id, created_at);
