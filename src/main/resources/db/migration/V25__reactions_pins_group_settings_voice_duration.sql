-- v1.6, P2: реакции, закреп, удаление группы, группы от сотрудников, длительность голосовых,
-- сводка AI по чатам.

-- 1. Реакции: одна реакция человека на сообщение, новая заменяет прежнюю.
CREATE TABLE IF NOT EXISTS birchat.chat_message_reactions (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    emoji VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chat_message_reactions_message FOREIGN KEY (message_id) REFERENCES birchat.chat_messages(id),
    CONSTRAINT fk_chat_message_reactions_user FOREIGN KEY (user_id) REFERENCES birchat.users(id),
    CONSTRAINT uq_chat_message_reactions UNIQUE (message_id, user_id)
);

-- 2. Закреплённое сообщение чата (одно) и удаление группы (мягкое: чат скрыт у всех).
ALTER TABLE birchat.chats
    ADD COLUMN IF NOT EXISTS pinned_message_id UUID,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID;

ALTER TABLE birchat.chats DROP CONSTRAINT IF EXISTS fk_chats_pinned_message;
ALTER TABLE birchat.chats
    ADD CONSTRAINT fk_chats_pinned_message
        FOREIGN KEY (pinned_message_id) REFERENCES birchat.chat_messages(id);

-- 3. Настройка компании: группы могут создавать все сотрудники (по умолчанию — только директор).
ALTER TABLE birchat.companies
    ADD COLUMN IF NOT EXISTS members_can_create_groups BOOLEAN NOT NULL DEFAULT FALSE;

-- 4. Длительность голосового и видео, которую передаёт приложение.
ALTER TABLE birchat.company_files
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT;

-- 5. Сводка директора с разбивкой по чатам: JSON [{ "chatId", "name", "summary" }].
ALTER TABLE birchat.ai_company_summaries
    ADD COLUMN IF NOT EXISTS sections_json TEXT;
