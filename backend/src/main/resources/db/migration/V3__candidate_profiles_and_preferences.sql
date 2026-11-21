-- Candidate profile (1:1 with users) and career preferences (1:1 with users).

CREATE TABLE candidate_profiles (
    user_id                  UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    full_name                VARCHAR(120) NOT NULL,
    headline                 VARCHAR(160),
    bio                      VARCHAR(2000),
    city                     VARCHAR(100),
    country_code             VARCHAR(2),
    career_stage             VARCHAR(30),
    graduation_year          INTEGER,
    linkedin_url             VARCHAR(300),
    github_url               VARCHAR(300),
    portfolio_url            VARCHAR(300),
    onboarding_completed_at  TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_profiles_career_stage CHECK (career_stage IN
        ('STUDENT', 'RECENT_GRADUATE', 'JOB_SEEKER', 'CAREER_CHANGER', 'EMPLOYED')),
    CONSTRAINT ck_profiles_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_profiles_graduation_year CHECK (graduation_year BETWEEN 1950 AND 2100)
);

CREATE TABLE candidate_preferences (
    user_id              UUID          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    desired_roles        TEXT[]        NOT NULL DEFAULT '{}',
    preferred_locations  TEXT[]        NOT NULL DEFAULT '{}',
    employment_types     TEXT[]        NOT NULL DEFAULT '{}',
    remote_preference    VARCHAR(20),
    willing_to_relocate  BOOLEAN       NOT NULL DEFAULT FALSE,
    expected_min_salary  INTEGER,
    salary_currency      VARCHAR(3),
    available_from       DATE,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_prefs_remote CHECK (remote_preference IN ('ONSITE', 'HYBRID', 'REMOTE', 'ANY')),
    CONSTRAINT ck_prefs_salary CHECK (expected_min_salary IS NULL OR expected_min_salary >= 0),
    CONSTRAINT ck_prefs_currency_format CHECK (salary_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_prefs_salary_currency CHECK (
        (expected_min_salary IS NULL AND salary_currency IS NULL)
        OR (expected_min_salary IS NOT NULL AND salary_currency IS NOT NULL))
);
