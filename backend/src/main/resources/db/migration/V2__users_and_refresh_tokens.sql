-- Accounts and refresh-token sessions.

CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    -- Normalized (trimmed, lower-cased) by the application before storage.
    email          VARCHAR(254) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    -- Incremented on password change; access tokens carrying an older value are rejected.
    token_version  INTEGER      NOT NULL DEFAULT 0,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('CANDIDATE', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- Opaque refresh tokens: only a SHA-256 hash is stored. Tokens issued from one login share a
-- family_id so that reuse of a rotated token can revoke the whole session.
CREATE TABLE refresh_tokens (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id    UUID        NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
