-- AI call metering, question bank (with pgvector embeddings), company packs and versioned rubrics.

CREATE TABLE ai_call_logs (
    id             bigserial PRIMARY KEY,
    request_id     text,
    user_id        uuid REFERENCES users (id) ON DELETE SET NULL,
    session_id     uuid,
    purpose        text        NOT NULL,
    provider       text        NOT NULL,
    model          text        NOT NULL,
    prompt_id      text        NOT NULL,
    prompt_version text        NOT NULL,
    input_tokens   int         NOT NULL DEFAULT 0,
    output_tokens  int         NOT NULL DEFAULT 0,
    latency_ms     int         NOT NULL DEFAULT 0,
    cache_hit      boolean     NOT NULL DEFAULT false,
    status         text        NOT NULL CHECK (status IN ('OK', 'ERROR', 'TIMEOUT', 'CIRCUIT_OPEN', 'BUDGET_EXCEEDED', 'INVALID_OUTPUT')),
    error_code     text,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_call_logs_user_created ON ai_call_logs (user_id, created_at);
CREATE INDEX ix_ai_call_logs_session ON ai_call_logs (session_id);
CREATE INDEX ix_ai_call_logs_created_brin ON ai_call_logs USING brin (created_at);

-- Type-specific details (DSA signature/tests/hints, LLD requirements, behavioural guidance) live in `detail`
-- (jsonb), validated by Java records on write. Common, queried fields are real columns.
CREATE TABLE questions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    type            text        NOT NULL CHECK (type IN ('DSA', 'LLD', 'BEHAVIOURAL')),
    slug            text        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title           text        NOT NULL CHECK (char_length(title) BETWEEN 3 AND 160),
    body_md         text        NOT NULL,
    difficulty      text        NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    status          text        NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    tags            text[]      NOT NULL DEFAULT '{}',
    companies       text[]      NOT NULL DEFAULT '{}',
    detail          jsonb       NOT NULL,
    source          text        NOT NULL DEFAULT 'ADMIN' CHECK (source IN ('SEED', 'ADMIN')),
    embedding       vector(1024),
    embedding_model text,
    embedded_at     timestamptz,
    created_by      uuid REFERENCES users (id) ON DELETE SET NULL,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_questions_select ON questions (type, status, difficulty);
CREATE INDEX ix_questions_tags ON questions USING gin (tags);
CREATE INDEX ix_questions_companies ON questions USING gin (companies);
CREATE INDEX ix_questions_search ON questions USING gin (to_tsvector('english', title || ' ' || body_md));
CREATE INDEX ix_questions_embedding ON questions USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE TABLE question_packs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug           text    NOT NULL UNIQUE,
    name           text    NOT NULL,
    description    text    NOT NULL,
    mode           text    NOT NULL CHECK (mode IN ('DSA', 'LLD', 'BEHAVIOURAL')),
    company        text,
    pro            boolean NOT NULL DEFAULT false,
    question_slugs text[]  NOT NULL
);

CREATE TABLE rubrics (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    key        text        NOT NULL CHECK (key IN ('DSA', 'LLD', 'BEHAVIOURAL', 'RESUME_DEEP_DIVE')),
    version    int         NOT NULL CHECK (version > 0),
    active     boolean     NOT NULL DEFAULT false,
    definition jsonb       NOT NULL,
    changelog  text        NOT NULL DEFAULT '',
    created_by uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (key, version)
);
CREATE UNIQUE INDEX ux_rubrics_one_active ON rubrics (key) WHERE active;
