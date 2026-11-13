-- Голосовые за последние 3 дня — в очередь на расшифровку.
--
-- Попытка через 3 минуты: при деплое старая версия приложения ещё около минуты работает
-- и пометила бы голосовые как «формат не поддерживается» (SKIPPED). Файлы с попыткой
-- в будущем она не берёт (так же сделано в V19).
UPDATE birchat.company_files
SET ai_status = 'PENDING',
    ai_attempts = 0,
    ai_error = NULL,
    ai_next_attempt_at = NOW() + INTERVAL '3 minutes'
WHERE status = 'READY'
  AND content_type LIKE 'audio/%'
  AND created_at > NOW() - INTERVAL '3 days'
  AND (ai_status IS NULL OR ai_status = 'SKIPPED');
