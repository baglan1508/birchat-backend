-- Фото и PDF за последние 3 дня — обработать заново.
-- Фото: раньше модель давала 1–3 предложения, теперь переписывает весь текст (все позиции чека).
-- PDF: раньше не обрабатывались, теперь AI читает их текст.
UPDATE birchat.company_files
SET ai_status = 'PENDING',
    ai_attempts = 0,
    ai_next_attempt_at = NULL,
    ai_error = NULL
WHERE status = 'READY'
  AND created_at > NOW() - INTERVAL '3 days'
  AND (
        (content_type LIKE 'image/%' AND ai_status IN ('DONE', 'FAILED'))
        OR (content_type = 'application/pdf' AND ai_status IS NULL)
  );
