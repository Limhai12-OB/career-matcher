-- Versioned, deterministic match results and their AI/template explanations.

CREATE TABLE job_matches (
    id                 UUID          PRIMARY KEY,
    user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id             UUID          NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    algorithm_version  VARCHAR(20)   NOT NULL,
    total_score        NUMERIC(4, 1) NOT NULL,
    -- Full component breakdown with factors (MatchResult), and the weights snapshot used.
    breakdown          JSONB         NOT NULL,
    weights            JSONB         NOT NULL,
    cv_analysis_id     UUID          REFERENCES cv_analyses (id) ON DELETE SET NULL,
    -- SHA-256 of the result content: a new row is stored only when the result changes.
    result_hash        VARCHAR(64)   NOT NULL,
    computed_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_job_matches_score CHECK (total_score BETWEEN 0 AND 100),
    CONSTRAINT ck_job_matches_hash CHECK (result_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT uq_job_matches_result UNIQUE (user_id, job_id, algorithm_version, result_hash)
);

CREATE INDEX ix_job_matches_latest ON job_matches (user_id, job_id, computed_at DESC);

CREATE TABLE match_explanations (
    id              UUID          PRIMARY KEY,
    match_id        UUID          NOT NULL REFERENCES job_matches (id) ON DELETE CASCADE,
    provider        VARCHAR(40)   NOT NULL,
    model           VARCHAR(80)   NOT NULL,
    prompt_version  VARCHAR(40)   NOT NULL,
    content         JSONB         NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_match_explanations_match UNIQUE (match_id)
);
