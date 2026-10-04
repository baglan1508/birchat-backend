CREATE TABLE IF NOT EXISTS birchat.ai_company_summaries (
                                                            id UUID PRIMARY KEY,
                                                            company_id UUID NOT NULL,
                                                            title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    items_text TEXT NOT NULL DEFAULT '',
    model VARCHAR(100),
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ai_company_summaries_company
    FOREIGN KEY (company_id)
    REFERENCES birchat.companies(id)
    );

CREATE INDEX IF NOT EXISTS idx_ai_company_summaries_company_generated_at
    ON birchat.ai_company_summaries(company_id, generated_at DESC);