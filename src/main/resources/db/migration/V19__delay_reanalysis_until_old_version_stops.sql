-- Повторная обработка из V18 начнётся через 3 минуты после миграции.
--
-- При деплое Render ещё около минуты держит старую версию приложения. Её фоновая обработка
-- успевала забрать файлы, поставленные в очередь V18, и снова сделать короткое описание фото
-- (на тесте так и случилось). Старая версия не берёт файлы с ai_next_attempt_at в будущем.
UPDATE birchat.company_files
SET ai_next_attempt_at = NOW() + INTERVAL '3 minutes'
WHERE ai_status = 'PENDING'
  AND ai_attempts = 0
  AND ai_next_attempt_at IS NULL
  AND created_at > NOW() - INTERVAL '3 days';
