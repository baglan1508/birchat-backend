
CREATE TABLE ai_request_quota (
                                  scope VARCHAR(24) NOT NULL,
                                  subject_key VARCHAR(80) NOT NULL,
                                  window_start TIMESTAMPTZ NOT NULL,
                                  request_count INTEGER NOT NULL DEFAULT 0,
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                  CONSTRAINT pk_ai_request_quota
                                      PRIMARY KEY (scope, subject_key, window_start),

                                  CONSTRAINT chk_ai_request_quota_count
                                      CHECK (request_count >= 0),

                                  CONSTRAINT chk_ai_request_quota_scope
                                      CHECK (scope IN (
                                                       'USER_MINUTE',
                                                       'USER_DAY',
                                                       'COMPANY_DAY'
                                          ))
);

CREATE INDEX idx_ai_request_quota_window_start
    ON ai_request_quota (window_start);
