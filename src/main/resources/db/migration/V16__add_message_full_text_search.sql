-- Полнотекстовый поиск по сообщениям с русской морфологией.
--
-- Раньше поиск шёл подстрокой (LOWER + LOCATE) по всем сообщениям компании — без индекса
-- и без морфологии: «договор» не находил «договора», «договором».
--
-- search_text: ASCII-знаки препинания заменяются пробелами, чтобы номера, телефоны, даты
-- и коды разбивались на слова одинаково в тексте и в запросе:
-- «INV-2024-15», «123-45-67», «10.11.2026», «отчет_2024.xlsx» → отдельные слова.
-- Та же функция применяется к запросу пользователя (ChatMessageRepository.searchAccessibleMessageIds),
-- поэтому текст и запрос нормализуются одинаково.
CREATE OR REPLACE FUNCTION birchat.search_text(value TEXT)
    RETURNS TEXT
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    RETURNS NULL ON NULL INPUT
AS $$
    SELECT regexp_replace(value, '[\x21-\x2F\x3A-\x40\x5B-\x60\x7B-\x7E]', ' ', 'g')
$$;

-- Вектор для поиска считается самой базой при вставке и изменении сообщения.
-- Приложение эту колонку не читает и не пишет.
ALTER TABLE birchat.chat_messages
    ADD COLUMN IF NOT EXISTS search_vector TSVECTOR
        GENERATED ALWAYS AS (
            to_tsvector('russian'::regconfig, birchat.search_text(COALESCE(content, '')))
        ) STORED;

CREATE INDEX IF NOT EXISTS idx_chat_messages_search_vector
    ON birchat.chat_messages USING GIN (search_vector);
