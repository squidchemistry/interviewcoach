-- Interview engine (state machine), transcript, code runs, evaluation and progress.

CREATE TABLE interview_sessions (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mode                  text        NOT NULL CHECK (mode IN ('DSA', 'LLD', 'BEHAVIOURAL', 'RESUME_DEEP_DIVE')),
    status                text        NOT NULL CHECK (status IN ('CREATED', 'IN_PROGRESS', 'EVALUATING', 'COMPLETED', 'ABANDONED')),
    stage                 text CHECK (stage IN ('QUESTION', 'ANSWER', 'FOLLOW_UP')),
    processing            boolean     NOT NULL DEFAULT false,
    target_role           text        NOT NULL CHECK (target_role IN ('SDE_1', 'SDE_2')),
    company               text,
    pack_slug             text,
    resume_id             uuid,
    rubric_id             uuid        NOT NULL REFERENCES rubrics (id),
    voice_enabled         boolean     NOT NULL DEFAULT false,
    planned_questions     smallint    NOT NULL CHECK (planned_questions BETWEEN 1 AND 6),
    current_index         smallint    NOT NULL DEFAULT -1,
    current_difficulty    text        NOT NULL CHECK (current_difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    seed                  bigint      NOT NULL,
    integrity             jsonb       NOT NULL DEFAULT '[]',
    plan                  jsonb       NOT NULL DEFAULT '{}',
    end_reason            text CHECK (end_reason IN ('COMPLETED_ALL', 'ENDED_BY_USER', 'INACTIVITY', 'ABANDONED_BY_USER', 'BUDGET_EXHAUSTED')),
    version               bigint      NOT NULL DEFAULT 0,
    started_at            timestamptz,
    last_activity_at      timestamptz NOT NULL DEFAULT now(),
    ended_at              timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'IN_PROGRESS') = (stage IS NOT NULL))
);
CREATE INDEX ix_sessions_user_created ON interview_sessions (user_id, created_at DESC);
CREATE INDEX ix_sessions_sweep ON interview_sessions (status, last_activity_at) WHERE status IN ('IN_PROGRESS', 'EVALUATING');

CREATE TABLE session_questions (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id        uuid        NOT NULL REFERENCES interview_sessions (id) ON DELETE CASCADE,
    position          smallint    NOT NULL,
    question_id       uuid REFERENCES questions (id) ON DELETE SET NULL,
    title             text        NOT NULL,
    prompt_md         text        NOT NULL,
    difficulty        text        NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    difficulty_reason text        NOT NULL,
    tags              text[]      NOT NULL DEFAULT '{}',
    context           jsonb       NOT NULL DEFAULT '{}',
    hints_used        smallint    NOT NULL DEFAULT 0 CHECK (hints_used BETWEEN 0 AND 3),
    follow_ups        smallint    NOT NULL DEFAULT 0,
    turn_score        numeric(4, 2) CHECK (turn_score BETWEEN 0 AND 10),
    assessment        jsonb,
    started_at        timestamptz NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    UNIQUE (session_id, position)
);
CREATE INDEX ix_session_questions_question ON session_questions (question_id);

CREATE TABLE turns (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          uuid        NOT NULL REFERENCES interview_sessions (id) ON DELETE CASCADE,
    session_question_id uuid REFERENCES session_questions (id) ON DELETE CASCADE,
    seq                 int         NOT NULL,
    role                text        NOT NULL CHECK (role IN ('INTERVIEWER', 'CANDIDATE', 'SYSTEM')),
    kind                text        NOT NULL CHECK (kind IN ('QUESTION', 'ANSWER', 'FOLLOW_UP', 'HINT', 'CODE_RUN', 'SYSTEM_NOTICE')),
    content             text        NOT NULL,
    format              text        NOT NULL DEFAULT 'MARKDOWN' CHECK (format IN ('TEXT', 'MARKDOWN', 'CODE', 'MERMAID')),
    input_mode          text        NOT NULL DEFAULT 'TEXT' CHECK (input_mode IN ('TEXT', 'VOICE')),
    client_message_id   uuid,
    metadata            jsonb       NOT NULL DEFAULT '{}',
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (session_id, seq)
);
CREATE UNIQUE INDEX ux_turns_idempotency ON turns (session_id, client_message_id) WHERE client_message_id IS NOT NULL;

CREATE TABLE code_submissions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_question_id uuid        NOT NULL REFERENCES session_questions (id) ON DELETE CASCADE,
    user_id             uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind                text        NOT NULL CHECK (kind IN ('RUN', 'SUBMIT')),
    language            text        NOT NULL CHECK (language IN ('PYTHON', 'JAVA', 'CPP', 'JAVASCRIPT')),
    source              text        NOT NULL,
    verdict             text        NOT NULL,
    passed              smallint    NOT NULL,
    total               smallint    NOT NULL,
    results             jsonb       NOT NULL,
    runtime_ms          int,
    executor            text        NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_code_submissions_question ON code_submissions (session_question_id, created_at DESC);

CREATE TABLE evaluations (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id     uuid        NOT NULL UNIQUE REFERENCES interview_sessions (id) ON DELETE CASCADE,
    rubric_id      uuid        NOT NULL REFERENCES rubrics (id),
    status         text        NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    overall_score  numeric(5, 1) CHECK (overall_score BETWEEN 0 AND 100),
    confidence     text CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    summary        text,
    strengths      jsonb,
    gaps           jsonb,
    next_steps     jsonb,
    per_question   jsonb,
    integrity      jsonb,
    provider       text,
    model          text,
    prompt_version text,
    attempts       smallint    NOT NULL DEFAULT 0,
    error          text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    completed_at   timestamptz
);
CREATE INDEX ix_evaluations_pending ON evaluations (status, updated_at) WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE competency_scores (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_id  uuid          NOT NULL REFERENCES evaluations (id) ON DELETE CASCADE,
    user_id        uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mode           text          NOT NULL,
    competency     text          NOT NULL CHECK (competency IN ('PROBLEM_SOLVING', 'CODE_QUALITY', 'COMMUNICATION', 'DESIGN', 'EDGE_CASES')),
    score          numeric(4, 2) NOT NULL CHECK (score BETWEEN 0 AND 10),
    weight         numeric(4, 3) NOT NULL,
    low_confidence boolean       NOT NULL DEFAULT false,
    rationale      text          NOT NULL,
    evidence       jsonb         NOT NULL DEFAULT '[]',
    created_at     timestamptz   NOT NULL DEFAULT now(),
    UNIQUE (evaluation_id, competency)
);
CREATE INDEX ix_competency_scores_trend ON competency_scores (user_id, competency, created_at);

-- Per-topic performance, also used as the SM-2 spaced-repetition schedule.
CREATE TABLE topic_stats (
    user_id          uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tag              text          NOT NULL,
    attempts         int           NOT NULL DEFAULT 0,
    avg_score        numeric(4, 2) NOT NULL DEFAULT 0,
    last_score       numeric(4, 2) NOT NULL DEFAULT 0,
    last_seen_at     timestamptz   NOT NULL DEFAULT now(),
    sr_repetitions   smallint      NOT NULL DEFAULT 0,
    sr_ease          numeric(4, 2) NOT NULL DEFAULT 2.5,
    sr_interval_days int           NOT NULL DEFAULT 0,
    sr_due_at        timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tag)
);
CREATE INDEX ix_topic_stats_weak ON topic_stats (user_id, avg_score);
CREATE INDEX ix_topic_stats_due ON topic_stats (user_id, sr_due_at);

CREATE TABLE usage_monthly (
    user_id            uuid    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    period             char(7) NOT NULL,
    interviews_started int     NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, period)
);
