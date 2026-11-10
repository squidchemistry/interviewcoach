-- Identity, profile, refresh tokens and audit log (docs/03-data-model.md §1, §4).
-- Enumerations are stored as text with CHECK constraints rather than PostgreSQL ENUM types: adding a value is a
-- simple constraint change inside a transaction, and values can be removed (ENUM values cannot).

CREATE TABLE users (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email          citext      NOT NULL,
    password_hash  text,
    name           text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 80),
    role           text        NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    plan           text        NOT NULL DEFAULT 'FREE' CHECK (plan IN ('FREE', 'PRO')),
    google_sub     text,
    email_verified boolean     NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    version        bigint      NOT NULL DEFAULT 0,
    -- A live account needs at least one way to sign in.
    CONSTRAINT users_login_method CHECK (deleted_at IS NOT NULL OR password_hash IS NOT NULL OR google_sub IS NOT NULL)
);
-- Deleted accounts are anonymised (email rewritten), so the unique index can cover all rows.
CREATE UNIQUE INDEX ux_users_email ON users (email);
CREATE UNIQUE INDEX ux_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;
CREATE INDEX ix_users_created_at ON users (created_at DESC);

CREATE TABLE companies (
    id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL UNIQUE CHECK (char_length(name) BETWEEN 1 AND 80),
    slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE profiles (
    user_id            uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    target_role        text          NOT NULL CHECK (target_role IN ('SDE_1', 'SDE_2')),
    experience_years   numeric(3, 1) NOT NULL
        CHECK (experience_years BETWEEN 0 AND 30 AND experience_years * 2 = floor(experience_years * 2)),
    preferred_language text          NOT NULL CHECK (preferred_language IN ('PYTHON', 'JAVA', 'CPP', 'JAVASCRIPT')),
    onboarded_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE user_target_companies (
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company_id uuid NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, company_id)
);
CREATE INDEX ix_user_target_companies_company ON user_target_companies (company_id);

CREATE TABLE refresh_tokens (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id      uuid        NOT NULL,
    token_hash     text        NOT NULL UNIQUE,
    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz,
    replaced_by_id uuid REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    user_agent     text CHECK (char_length(user_agent) <= 512),
    ip             text CHECK (char_length(ip) <= 45),
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE audit_logs (
    id          bigserial PRIMARY KEY,
    actor_id    uuid REFERENCES users (id) ON DELETE SET NULL,
    action      text        NOT NULL,
    entity_type text        NOT NULL,
    entity_id   uuid,
    before      jsonb,
    after       jsonb,
    request_id  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX ix_audit_logs_created_brin ON audit_logs USING brin (created_at);
