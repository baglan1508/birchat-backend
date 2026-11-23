-- v1.6, P0.
--
-- 1. Согласие компании на AI. Пока флаг выключен, сообщения и файлы компании не уходят
--    стороннему AI: нет сводки по расписанию, /ai/ask и сводка отвечают 403 AI_DISABLED,
--    фото и PDF не анализируются. Новые компании — выключено; у существующих включено,
--    чтобы директора не потеряли сводку без предупреждения.
ALTER TABLE birchat.companies
    ADD COLUMN IF NOT EXISTS ai_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE birchat.companies SET ai_enabled = TRUE;

-- 2. Сотрудник, которого добавил директор, «не зашёл» в компанию, пока сам не открыл
--    приложение (GET /companies/my). До этого коллеги видят его под именем, которое ввёл
--    директор (invited_name), а не под именем из его профиля: по номеру телефона нельзя
--    узнать, как человек подписан в BirChat.
ALTER TABLE birchat.company_members
    ADD COLUMN IF NOT EXISTS invited_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP;

UPDATE birchat.company_members
SET accepted_at = COALESCE(joined_at, NOW())
WHERE accepted_at IS NULL;
