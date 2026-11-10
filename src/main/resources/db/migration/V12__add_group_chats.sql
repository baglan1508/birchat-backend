-- Групповые чаты: в компании один GENERAL-чат (все сотрудники) и сколько угодно GROUP-чатов
-- с явным списком участников. Создаёт группы и управляет участниками только директор.

-- 1. Раньше на компанию допускался один чат каждого типа. Теперь уникален только GENERAL.
ALTER TABLE birchat.chats DROP CONSTRAINT IF EXISTS uq_company_chat_type;

CREATE UNIQUE INDEX IF NOT EXISTS uq_chats_company_general
    ON birchat.chats (company_id)
    WHERE type = 'GENERAL';

ALTER TABLE birchat.chats
    ADD COLUMN IF NOT EXISTS created_by UUID NULL;

ALTER TABLE birchat.chats
    DROP CONSTRAINT IF EXISTS fk_chats_created_by;

ALTER TABLE birchat.chats
    ADD CONSTRAINT fk_chats_created_by
        FOREIGN KEY (created_by) REFERENCES birchat.users(id);

-- 2. Участники групповых чатов. Для GENERAL участники не хранятся: это все активные сотрудники.
CREATE TABLE IF NOT EXISTS birchat.chat_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL,
    user_id UUID NOT NULL,
    added_by UUID NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_chat_members_chat
        FOREIGN KEY (chat_id) REFERENCES birchat.chats(id) ON DELETE CASCADE,

    CONSTRAINT fk_chat_members_user
        FOREIGN KEY (user_id) REFERENCES birchat.users(id),

    CONSTRAINT fk_chat_members_added_by
        FOREIGN KEY (added_by) REFERENCES birchat.users(id),

    CONSTRAINT uq_chat_members_chat_user
        UNIQUE (chat_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_members_user
    ON birchat.chat_members (user_id);

-- 3. Файл может принадлежать конкретному групповому чату: тогда его видят только участники.
--    NULL — файл компании, виден всем сотрудникам.
ALTER TABLE birchat.company_files
    ADD COLUMN IF NOT EXISTS chat_id UUID NULL;

ALTER TABLE birchat.company_files
    DROP CONSTRAINT IF EXISTS fk_company_files_chat;

ALTER TABLE birchat.company_files
    ADD CONSTRAINT fk_company_files_chat
        FOREIGN KEY (chat_id) REFERENCES birchat.chats(id);

CREATE INDEX IF NOT EXISTS idx_company_files_chat
    ON birchat.company_files (chat_id)
    WHERE chat_id IS NOT NULL;

-- 4. У каждой компании должен быть общий чат (на случай старых данных без него).
INSERT INTO birchat.chats (id, company_id, name, type, created_at)
SELECT gen_random_uuid(), c.id, 'Общий чат компании', 'GENERAL', NOW()
FROM birchat.companies c
WHERE NOT EXISTS (
    SELECT 1
    FROM birchat.chats ch
    WHERE ch.company_id = c.id
      AND ch.type = 'GENERAL'
);
