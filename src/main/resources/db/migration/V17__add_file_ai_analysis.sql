-- Описание файлов для AI: что на фото (позже — расшифровка голосовых).
-- Обрабатывается в фоне (FileAnalysisService), результат попадает в контекст сводок и вопросов к AI.
--
-- ai_status:
--   NULL     — файл не обрабатывается (документы, старые файлы);
--   PENDING  — ждёт обработки (или повторной попытки после ai_next_attempt_at);
--   DONE     — готово, текст в ai_text;
--   FAILED   — не получилось за несколько попыток;
--   SKIPPED  — формат или размер не подходит модели.
ALTER TABLE birchat.company_files
    ADD COLUMN IF NOT EXISTS ai_status VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS ai_text TEXT NULL,
    ADD COLUMN IF NOT EXISTS ai_model VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS ai_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ai_error VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS ai_next_attempt_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS ai_processed_at TIMESTAMP NULL;

ALTER TABLE birchat.company_files
    DROP CONSTRAINT IF EXISTS chk_company_files_ai_status;

ALTER TABLE birchat.company_files
    ADD CONSTRAINT chk_company_files_ai_status
        CHECK (ai_status IS NULL OR ai_status IN ('PENDING', 'DONE', 'FAILED', 'SKIPPED'));

-- Очередь на обработку: только ожидающие файлы.
CREATE INDEX IF NOT EXISTS idx_company_files_ai_pending
    ON birchat.company_files (created_at)
    WHERE ai_status = 'PENDING';

-- Сводка пересчитывается, если после неё появились новые описания файлов компании.
CREATE INDEX IF NOT EXISTS idx_company_files_ai_processed
    ON birchat.company_files (company_id, ai_processed_at)
    WHERE ai_status = 'DONE';

-- Фото последних трёх дней тоже получат описания.
UPDATE birchat.company_files
SET ai_status = 'PENDING'
WHERE ai_status IS NULL
  AND status = 'READY'
  AND content_type LIKE 'image/%'
  AND created_at > NOW() - INTERVAL '3 days';
