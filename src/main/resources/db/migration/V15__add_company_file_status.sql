-- Прямая загрузка файлов в Supabase: запись создаётся до загрузки (PENDING)
-- и становится доступной после подтверждения (READY). Существующие файлы — READY.
ALTER TABLE birchat.company_files
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'READY';

ALTER TABLE birchat.company_files
    DROP CONSTRAINT IF EXISTS chk_company_files_status;

ALTER TABLE birchat.company_files
    ADD CONSTRAINT chk_company_files_status CHECK (status IN ('PENDING', 'READY'));

-- Для ночной чистки недозагруженных файлов.
CREATE INDEX IF NOT EXISTS idx_company_files_pending_created
    ON birchat.company_files (created_at)
    WHERE status = 'PENDING';
