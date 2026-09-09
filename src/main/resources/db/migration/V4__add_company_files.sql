CREATE TABLE IF NOT EXISTS birchat.company_files (
                                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    company_id UUID NOT NULL,
    uploaded_by UUID NOT NULL,

    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT NOT NULL,

    storage_key TEXT NOT NULL,
    file_url TEXT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_company_files_company
    FOREIGN KEY (company_id)
    REFERENCES birchat.companies(id),

    CONSTRAINT fk_company_files_uploaded_by
    FOREIGN KEY (uploaded_by)
    REFERENCES birchat.users(id)
    );

CREATE INDEX IF NOT EXISTS idx_company_files_company_created
    ON birchat.company_files (company_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_company_files_uploaded_by
    ON birchat.company_files (uploaded_by);