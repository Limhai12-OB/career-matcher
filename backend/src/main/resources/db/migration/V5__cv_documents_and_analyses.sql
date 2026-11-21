-- Uploaded CV files (content lives encrypted in private object storage) and their analyses.

CREATE TABLE cv_documents (
    id                 UUID          PRIMARY KEY,
    user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    storage_key        VARCHAR(200)  NOT NULL,
    original_filename  VARCHAR(255)  NOT NULL,
    document_type      VARCHAR(10)   NOT NULL,
    size_bytes         INTEGER       NOT NULL,
    sha256             VARCHAR(64)   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_cv_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_cv_documents_type CHECK (document_type IN ('PDF', 'DOCX')),
    CONSTRAINT ck_cv_documents_size CHECK (size_bytes > 0),
    CONSTRAINT ck_cv_documents_sha CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_cv_documents_user ON cv_documents (user_id, created_at DESC);

-- One row per analysis run; re-analysis adds rows, so history is preserved.
CREATE TABLE cv_analyses (
    id               UUID          PRIMARY KEY,
    document_id      UUID          NOT NULL REFERENCES cv_documents (id) ON DELETE CASCADE,
    user_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status           VARCHAR(20)   NOT NULL,
    error_code       VARCHAR(40),
    provider         VARCHAR(40),
    model            VARCHAR(80),
    prompt_version   VARCHAR(40),
    schema_version   VARCHAR(40),
    -- Validated, grounded extraction (education, experience, projects, certifications, stats).
    result           JSONB,
    warnings         TEXT[]        NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ   NOT NULL,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    applied_at       TIMESTAMPTZ,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_cv_analyses_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_cv_analyses_result CHECK (status <> 'COMPLETED' OR result IS NOT NULL),
    CONSTRAINT ck_cv_analyses_error CHECK (status <> 'FAILED' OR error_code IS NOT NULL)
);

CREATE INDEX ix_cv_analyses_user ON cv_analyses (user_id, created_at DESC);
CREATE INDEX ix_cv_analyses_document ON cv_analyses (document_id, created_at DESC);
CREATE INDEX ix_cv_analyses_active ON cv_analyses (status, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');

-- Skills found in an analysis, normalized against the catalog (skill_id NULL = not recognized).
CREATE TABLE cv_analysis_skills (
    id           UUID          PRIMARY KEY,
    analysis_id  UUID          NOT NULL REFERENCES cv_analyses (id) ON DELETE CASCADE,
    position     INTEGER       NOT NULL,
    raw_name     VARCHAR(80)   NOT NULL,
    skill_id     UUID          REFERENCES skills (id),
    category     VARCHAR(30)   NOT NULL,
    evidence     VARCHAR(300)  NOT NULL
);

CREATE INDEX ix_cv_analysis_skills_analysis ON cv_analysis_skills (analysis_id, position);

-- The candidate's confirmed skill set (used by matching). CV-sourced rows keep a link to the
-- analysis they came from; deleting the CV keeps the skill but clears the link.
CREATE TABLE candidate_skills (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_id     UUID          NOT NULL REFERENCES skills (id),
    source       VARCHAR(10)   NOT NULL,
    analysis_id  UUID          REFERENCES cv_analyses (id) ON DELETE SET NULL,
    evidence     VARCHAR(300),
    created_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_candidate_skills UNIQUE (user_id, skill_id),
    CONSTRAINT ck_candidate_skills_source CHECK (source IN ('CV', 'MANUAL'))
);

CREATE INDEX ix_candidate_skills_skill ON candidate_skills (skill_id);
