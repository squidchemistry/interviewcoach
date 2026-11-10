-- Resumes (stored in the database: ≤ 5 MB, deleted on request), subscriptions, payment webhooks, share links.

CREATE TABLE resumes (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename text        NOT NULL,
    size_bytes        int         NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    sha256            text        NOT NULL,
    page_count        int         NOT NULL,
    content           bytea       NOT NULL,
    status            text        NOT NULL CHECK (status IN ('UPLOADED', 'PARSING', 'PARSED', 'FAILED')),
    raw_text          text,
    parsed            jsonb,
    integrity         jsonb       NOT NULL DEFAULT '[]',
    error             text,
    active            boolean     NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_resumes_user ON resumes (user_id, created_at DESC);
CREATE UNIQUE INDEX ux_resumes_one_active ON resumes (user_id) WHERE active;

ALTER TABLE interview_sessions
    ADD CONSTRAINT fk_sessions_resume FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE SET NULL;

CREATE TABLE subscriptions (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider                 text        NOT NULL CHECK (provider IN ('RAZORPAY', 'MOCK')),
    provider_subscription_id text        NOT NULL UNIQUE,
    plan_id                  text        NOT NULL,
    status                   text        NOT NULL CHECK (status IN ('CREATED', 'AUTHENTICATED', 'ACTIVE', 'PENDING', 'HALTED', 'CANCELLED', 'COMPLETED', 'EXPIRED')),
    current_end              timestamptz,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_subscriptions_user ON subscriptions (user_id, created_at DESC);

CREATE TABLE payment_events (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider         text        NOT NULL,
    event_id         text        NOT NULL UNIQUE,
    event_type       text        NOT NULL,
    payload          jsonb       NOT NULL,
    received_at      timestamptz NOT NULL DEFAULT now(),
    processed_at     timestamptz,
    processing_error text
);

CREATE TABLE shared_reports (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id uuid        NOT NULL REFERENCES interview_sessions (id) ON DELETE CASCADE,
    token_hash text        NOT NULL UNIQUE,
    created_by uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    view_count int         NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_shared_reports_session ON shared_reports (session_id);
