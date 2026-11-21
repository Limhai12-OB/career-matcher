-- Job listings and their required/preferred catalog skills.

CREATE TABLE jobs (
    id                     UUID          PRIMARY KEY,
    title                  VARCHAR(150)  NOT NULL,
    company_name           VARCHAR(120)  NOT NULL,
    company_website        VARCHAR(500),
    description            TEXT          NOT NULL,
    city                   VARCHAR(100),
    country_code           VARCHAR(2),
    remote_type            VARCHAR(10)   NOT NULL,
    employment_type        VARCHAR(20)   NOT NULL,
    seniority              VARCHAR(20)   NOT NULL,
    min_experience_months  INTEGER       NOT NULL DEFAULT 0,
    max_experience_months  INTEGER,
    education_level        VARCHAR(20),
    education_fields       TEXT[]        NOT NULL DEFAULT '{}',
    salary_min             INTEGER,
    salary_max             INTEGER,
    salary_currency        VARCHAR(3),
    salary_period          VARCHAR(10),
    application_url        VARCHAR(500)  NOT NULL,
    source_name            VARCHAR(120)  NOT NULL,
    source_url             VARCHAR(500),
    -- REAL: a genuine opening entered by an admin. DEMO: demonstration data (never a real job).
    data_origin            VARCHAR(10)   NOT NULL,
    status                 VARCHAR(10)   NOT NULL,
    posted_on              DATE          NOT NULL,
    expires_on             DATE,
    published_at           TIMESTAMPTZ,
    created_by             UUID          REFERENCES users (id) ON DELETE SET NULL,
    updated_by             UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_jobs_description CHECK (char_length(description) BETWEEN 50 AND 20000),
    CONSTRAINT ck_jobs_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_jobs_remote CHECK (remote_type IN ('ONSITE', 'HYBRID', 'REMOTE')),
    CONSTRAINT ck_jobs_employment CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'INTERNSHIP', 'CONTRACT')),
    CONSTRAINT ck_jobs_seniority CHECK (seniority IN ('INTERNSHIP', 'ENTRY', 'JUNIOR')),
    CONSTRAINT ck_jobs_experience CHECK (min_experience_months BETWEEN 0 AND 240
        AND (max_experience_months IS NULL OR max_experience_months BETWEEN min_experience_months AND 240)),
    CONSTRAINT ck_jobs_education CHECK (education_level IN
        ('HIGH_SCHOOL', 'VOCATIONAL', 'ASSOCIATE', 'BACHELOR', 'MASTER')),
    CONSTRAINT ck_jobs_salary CHECK (
        (salary_min IS NULL AND salary_max IS NULL AND salary_currency IS NULL AND salary_period IS NULL)
        OR (salary_currency ~ '^[A-Z]{3}$' AND salary_period IN ('MONTH', 'YEAR')
            AND (salary_min IS NOT NULL OR salary_max IS NOT NULL)
            AND (salary_min IS NULL OR salary_min >= 0)
            AND (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min))),
    CONSTRAINT ck_jobs_urls CHECK (application_url ~ '^https://'
        AND (source_url IS NULL OR source_url ~ '^https://')
        AND (company_website IS NULL OR company_website ~ '^https://')),
    CONSTRAINT ck_jobs_origin CHECK (data_origin IN ('REAL', 'DEMO')),
    -- Demo listings may only link to reserved example domains (RFC 2606), never to real sites.
    CONSTRAINT ck_jobs_demo_urls CHECK (data_origin <> 'DEMO'
        OR application_url ~ '^https://([a-z0-9-]+\.)*example\.(com|org|net)(/|$)'),
    CONSTRAINT ck_jobs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT ck_jobs_dates CHECK (expires_on IS NULL OR expires_on >= posted_on)
);

CREATE INDEX ix_jobs_listing ON jobs (status, posted_on DESC, created_at DESC);
CREATE INDEX ix_jobs_country ON jobs (country_code);
CREATE INDEX ix_jobs_title_trgm ON jobs USING gin (lower(title) gin_trgm_ops);
CREATE INDEX ix_jobs_company_trgm ON jobs USING gin (lower(company_name) gin_trgm_ops);

CREATE TABLE job_skills (
    job_id       UUID         NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    skill_id     UUID         NOT NULL REFERENCES skills (id),
    requirement  VARCHAR(10)  NOT NULL,
    PRIMARY KEY (job_id, skill_id),
    CONSTRAINT ck_job_skills_requirement CHECK (requirement IN ('REQUIRED', 'PREFERRED'))
);

CREATE INDEX ix_job_skills_skill ON job_skills (skill_id);
