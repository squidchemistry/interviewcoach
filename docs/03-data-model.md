# 03 · Data Model (ER)

PostgreSQL 16 with extensions `pgvector`, `citext` and `pgcrypto`. All primary keys are `uuid` (`gen_random_uuid()`)
except append-only log tables (`bigserial`). Timestamps are `timestamptz`. Schema is owned by **Flyway** SQL migrations. JPA entities
are PascalCase mapped to snake_case tables (`@Table`), and Hibernate runs with `ddl-auto=validate`, so entity/schema
drift fails at startup.

The diagram is split into four domains for readability. Relationships across domains are repeated as stubs.

> These are the design-time diagrams. The shipped schema (migrations `V1`–`V5`, 21 tables) differs in places, for
> example one `questions` table with a `detail` jsonb column instead of per-type tables. The ER diagram of the
> shipped schema is in [10-architecture-and-er-diagrams.md](10-architecture-and-er-diagrams.md#6-er-diagram).

## 1. Identity, profile and resume

```mermaid
erDiagram
  USERS ||--o| PROFILES : "has"
  USERS ||--o{ USER_TARGET_COMPANIES : "targets"
  COMPANIES ||--o{ USER_TARGET_COMPANIES : "targeted by"
  USERS ||--o{ REFRESH_TOKENS : "owns"
  REFRESH_TOKENS |o--o| REFRESH_TOKENS : "replaced by"
  USERS ||--o{ RESUMES : "uploads"
  RESUMES ||--o{ RESUME_PROJECTS : "contains"

  USERS {
    uuid id PK
    citext email UK
    text password_hash "null for Google-only users"
    text name
    user_role role
    plan_tier plan
    text google_sub UK "nullable"
    boolean email_verified
    timestamptz created_at
    timestamptz updated_at
    timestamptz deleted_at "soft delete"
  }
  PROFILES {
    uuid user_id PK, FK
    target_role target_role
    decimal experience_years "0-30, step 0.5"
    code_language preferred_language
    timestamptz onboarded_at
    timestamptz updated_at
  }
  COMPANIES {
    uuid id PK
    text name UK
    text slug UK
  }
  USER_TARGET_COMPANIES {
    uuid user_id PK, FK
    uuid company_id PK, FK
  }
  REFRESH_TOKENS {
    uuid id PK
    uuid user_id FK
    uuid family_id "rotation chain"
    text token_hash UK "sha256 of opaque token"
    timestamptz expires_at
    timestamptz revoked_at
    uuid replaced_by_id FK
    text user_agent
    inet ip
    timestamptz created_at
  }
  RESUMES {
    uuid id PK
    uuid user_id FK
    text storage_key UK
    text original_filename
    int size_bytes
    text sha256
    int page_count
    resume_status status
    text raw_text "PII, never logged"
    jsonb parsed "ParsedResumeSchema"
    jsonb integrity_flags
    text prompt_version
    text error
    boolean is_active
    timestamptz created_at
    timestamptz updated_at
  }
  RESUME_PROJECTS {
    uuid id PK
    uuid resume_id FK
    smallint position
    text name
    text summary
    text_array tech_stack
    text_array highlights
  }
```

## 2. Question bank, packs and rubrics

```mermaid
erDiagram
  QUESTIONS ||--o{ QUESTION_TAGS : "tagged"
  TAGS ||--o{ QUESTION_TAGS : "applies to"
  QUESTIONS ||--o{ QUESTION_COMPANIES : "asked at"
  COMPANIES ||--o{ QUESTION_COMPANIES : "asks"
  QUESTIONS ||--o| DSA_PROBLEMS : "DSA detail"
  QUESTIONS ||--o| LLD_PROBLEMS : "LLD detail"
  QUESTIONS ||--o| BEHAVIOURAL_PROMPTS : "behavioural detail"
  QUESTIONS ||--o{ TEST_CASES : "has"
  QUESTIONS ||--o{ QUESTION_HINTS : "has"
  QUESTION_PACKS ||--o{ QUESTION_PACK_ITEMS : "includes"
  QUESTIONS ||--o{ QUESTION_PACK_ITEMS : "in"
  COMPANIES |o--o{ QUESTION_PACKS : "branded"
  USERS ||--o{ QUESTIONS : "created_by"
  USERS ||--o{ RUBRICS : "created_by"

  QUESTIONS {
    uuid id PK
    question_type type
    text slug UK
    text title
    text body_md
    difficulty difficulty
    question_status status
    int version "bumped on edit"
    vector embedding "vector(1024), nullable"
    text embedding_model
    timestamptz embedded_at
    uuid created_by FK
    timestamptz created_at
    timestamptz updated_at
    timestamptz archived_at
  }
  TAGS {
    uuid id PK
    text slug UK
    text name
    tag_category category
  }
  QUESTION_TAGS {
    uuid question_id PK, FK
    uuid tag_id PK, FK
  }
  QUESTION_COMPANIES {
    uuid question_id PK, FK
    uuid company_id PK, FK
  }
  DSA_PROBLEMS {
    uuid question_id PK, FK
    text function_name
    jsonb signature "params + return types"
    jsonb starter_code "lang -> code"
    jsonb driver_templates "optional override; default generated from signature"
    text constraints_md
    text expected_time_complexity
    text expected_space_complexity
    jsonb reference_solutions "admin only"
    output_comparator comparator
    int time_limit_ms
    int memory_limit_kb
  }
  TEST_CASES {
    uuid id PK
    uuid question_id FK
    smallint position
    text input "JSON args"
    text expected_output "JSON"
    boolean is_sample
    smallint weight
  }
  QUESTION_HINTS {
    uuid id PK
    uuid question_id FK
    smallint level "1..3, UK with question_id"
    text content_md "seed hint; AI personalises"
  }
  LLD_PROBLEMS {
    uuid question_id PK, FK
    text requirements_md
    text_array focus_areas "SOLID, CONCURRENCY..."
    text reference_design_md
    text reference_mermaid
  }
  BEHAVIOURAL_PROMPTS {
    uuid question_id PK, FK
    text what_good_looks_like_md
    text_array follow_up_probes
  }
  QUESTION_PACKS {
    uuid id PK
    text slug UK
    text name
    text description
    uuid company_id FK
    interview_mode mode
    boolean is_pro
    timestamptz created_at
  }
  QUESTION_PACK_ITEMS {
    uuid pack_id PK, FK
    uuid question_id PK, FK
    smallint position
  }
  RUBRICS {
    uuid id PK
    interview_mode key
    int version
    boolean is_active
    jsonb definition "competencies, weights, level descriptors"
    text changelog
    uuid created_by FK
    timestamptz created_at
  }
```

## 3. Sessions, evaluation and progress

```mermaid
erDiagram
  USERS ||--o{ INTERVIEW_SESSIONS : "takes"
  RUBRICS ||--o{ INTERVIEW_SESSIONS : "pinned version"
  RESUMES |o--o{ INTERVIEW_SESSIONS : "deep-dive source"
  QUESTION_PACKS |o--o{ INTERVIEW_SESSIONS : "drawn from"
  COMPANIES |o--o{ INTERVIEW_SESSIONS : "target"
  INTERVIEW_SESSIONS ||--o{ SESSION_QUESTIONS : "asks"
  QUESTIONS |o--o{ SESSION_QUESTIONS : "bank question"
  RESUME_PROJECTS |o--o{ SESSION_QUESTIONS : "project question"
  INTERVIEW_SESSIONS ||--o{ TURNS : "transcript"
  SESSION_QUESTIONS |o--o{ TURNS : "groups"
  SESSION_QUESTIONS ||--o{ CODE_SUBMISSIONS : "code runs"
  INTERVIEW_SESSIONS ||--o| EVALUATIONS : "evaluated by"
  EVALUATIONS ||--o{ COMPETENCY_SCORES : "scores"
  USERS ||--o{ TOPIC_STATS : "per tag"
  TAGS ||--o{ TOPIC_STATS : "tracked"
  INTERVIEW_SESSIONS ||--o{ SHARED_REPORTS : "shared via"

  INTERVIEW_SESSIONS {
    uuid id PK
    uuid user_id FK
    interview_mode mode
    session_status status
    session_stage stage "null unless IN_PROGRESS"
    target_role target_role
    uuid company_id FK
    uuid pack_id FK
    uuid resume_id FK
    uuid rubric_id FK
    boolean voice_enabled
    smallint planned_questions
    smallint current_index
    difficulty current_difficulty
    smallint follow_ups_in_current
    int token_budget_in
    int token_budget_out
    int tokens_in_used
    int tokens_out_used
    int seed "deterministic selection + mock"
    int version "optimistic lock"
    end_reason end_reason
    timestamptz started_at
    timestamptz last_activity_at
    timestamptz ended_at
    timestamptz created_at
    timestamptz updated_at
  }
  SESSION_QUESTIONS {
    uuid id PK
    uuid session_id FK
    smallint position "UK with session_id"
    uuid question_id FK "nullable"
    uuid resume_project_id FK "nullable"
    text generated_prompt_md "resume mode"
    difficulty difficulty
    text difficulty_reason
    smallint hints_used
    smallint follow_ups
    decimal turn_score "0-10"
    jsonb assessment "TurnAssessmentSchema"
    timestamptz started_at
    timestamptz completed_at
  }
  TURNS {
    uuid id PK
    uuid session_id FK
    uuid session_question_id FK
    int seq "UK with session_id"
    turn_role role
    turn_kind kind
    text content
    content_format format
    input_mode input_mode
    uuid client_message_id "UK with session_id, idempotency"
    jsonb metadata "language, hint level, stt confidence"
    int tokens_in
    int tokens_out
    timestamptz created_at
  }
  CODE_SUBMISSIONS {
    uuid id PK
    uuid session_question_id FK
    uuid user_id FK
    submission_kind kind
    code_language language
    text source
    text source_sha256
    execution_verdict verdict
    smallint passed
    smallint total
    jsonb results "per test; hidden redacted on read"
    int runtime_ms
    int memory_kb
    text executor "judge0 | mock"
    timestamptz created_at
  }
  EVALUATIONS {
    uuid id PK
    uuid session_id FK, UK
    uuid rubric_id FK
    evaluation_status status
    decimal overall_score "0-100, computed in code"
    evaluation_confidence confidence
    text summary
    jsonb strengths
    jsonb gaps
    jsonb next_steps "exactly 3"
    jsonb per_question "model answer, STAR breakdown"
    jsonb integrity_flags
    jsonb raw_output
    text provider
    text model
    text prompt_version
    smallint attempts
    text error
    timestamptz started_at
    timestamptz completed_at
    timestamptz created_at
  }
  COMPETENCY_SCORES {
    uuid id PK
    uuid evaluation_id FK
    uuid user_id FK "denormalised for dashboard"
    interview_mode mode "denormalised"
    competency competency
    decimal score "0-10"
    decimal weight
    boolean low_confidence
    text rationale
    jsonb evidence "verified quotes"
    timestamptz created_at
  }
  TOPIC_STATS {
    uuid user_id PK, FK
    uuid tag_id PK, FK
    int attempts
    decimal avg_score "EWMA"
    decimal last_score
    timestamptz last_seen_at
    smallint sr_repetitions "Phase 2 SM-2"
    decimal sr_ease_factor
    int sr_interval_days
    timestamptz sr_due_at
  }
  SHARED_REPORTS {
    uuid id PK
    uuid session_id FK
    text token_hash UK
    uuid created_by FK
    timestamptz expires_at
    timestamptz revoked_at
    int view_count
    timestamptz created_at
  }
```

## 4. Billing, usage and operations

```mermaid
erDiagram
  USERS ||--o{ SUBSCRIPTIONS : "pays"
  USERS ||--o{ USAGE_MONTHLY : "consumes"
  USERS |o--o{ AI_CALL_LOGS : "triggers"
  INTERVIEW_SESSIONS |o--o{ AI_CALL_LOGS : "within"
  USERS |o--o{ AUDIT_LOGS : "actor"

  SUBSCRIPTIONS {
    uuid id PK
    uuid user_id FK
    text provider "RAZORPAY | MOCK"
    text provider_subscription_id UK
    text provider_plan_id
    subscription_status status
    timestamptz current_start
    timestamptz current_end
    boolean cancel_at_period_end
    timestamptz created_at
    timestamptz updated_at
  }
  PAYMENT_EVENTS {
    uuid id PK
    text provider
    text provider_event_id UK "idempotency"
    text event_type
    jsonb payload
    timestamptz received_at
    timestamptz processed_at
    text processing_error
  }
  USAGE_MONTHLY {
    uuid user_id PK, FK
    char period PK "YYYY-MM"
    int interviews_started
    bigint tokens_in
    bigint tokens_out
  }
  AI_CALL_LOGS {
    bigserial id PK
    text request_id
    uuid user_id FK
    uuid session_id FK
    ai_purpose purpose
    text provider
    text model
    text prompt_id
    text prompt_version
    int input_tokens
    int output_tokens
    int latency_ms
    boolean cache_hit
    smallint attempt
    ai_call_status status
    text error_code
    timestamptz created_at
  }
  AUDIT_LOGS {
    bigserial id PK
    uuid actor_id FK
    text action
    text entity_type
    uuid entity_id
    jsonb before
    jsonb after
    text request_id
    timestamptz created_at
  }
```

> `text_array` in the diagrams means PostgreSQL `text[]`, and `vector` means `vector(1024)`. Mermaid's ER
> syntax doesn't accept those type spellings.

## 5. Enums

| Enum | Values |
|---|---|
| `user_role` | USER, ADMIN |
| `plan_tier` | FREE, PRO |
| `target_role` | SDE_1, SDE_2 |
| `code_language` | PYTHON, JAVA, CPP, JAVASCRIPT |
| `resume_status` | UPLOADED, PARSING, PARSED, FAILED |
| `question_type` | DSA, LLD, BEHAVIOURAL |
| `difficulty` | EASY, MEDIUM, HARD |
| `question_status` | DRAFT, PUBLISHED, ARCHIVED |
| `tag_category` | DSA_TOPIC, LLD_CONCEPT, BEHAVIOURAL_COMPETENCY, LEADERSHIP_PRINCIPLE, TECH_SKILL |
| `output_comparator` | EXACT, UNORDERED_LIST, FLOAT_TOLERANCE |
| `interview_mode` | DSA, LLD, BEHAVIOURAL, RESUME_DEEP_DIVE |
| `session_status` | CREATED, IN_PROGRESS, EVALUATING, COMPLETED, ABANDONED |
| `session_stage` | QUESTION, ANSWER, FOLLOW_UP |
| `end_reason` | COMPLETED_ALL, ENDED_BY_USER, INACTIVITY, ABANDONED_BY_USER, BUDGET_EXHAUSTED |
| `turn_role` | INTERVIEWER, CANDIDATE, SYSTEM |
| `turn_kind` | QUESTION, ANSWER, FOLLOW_UP, HINT, CODE_RUN, CLARIFICATION, SYSTEM_NOTICE |
| `content_format` | TEXT, MARKDOWN, CODE, MERMAID |
| `input_mode` | TEXT, VOICE |
| `submission_kind` | RUN, SUBMIT |
| `execution_verdict` | ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILATION_ERROR, INTERNAL_ERROR |
| `evaluation_status` | PENDING, RUNNING, SUCCEEDED, FAILED |
| `evaluation_confidence` | HIGH, MEDIUM, LOW |
| `competency` | PROBLEM_SOLVING, CODE_QUALITY, COMMUNICATION, DESIGN, EDGE_CASES |
| `ai_purpose` | INTERVIEWER_TURN, TURN_ASSESSMENT, HINT, RESUME_EXTRACTION, QUESTION_GENERATION, EVALUATION, EVALUATION_REPAIR, EMBEDDING |
| `ai_call_status` | OK, ERROR, TIMEOUT, CIRCUIT_OPEN, BUDGET_EXCEEDED, INVALID_OUTPUT |
| `subscription_status` | CREATED, AUTHENTICATED, ACTIVE, PENDING, HALTED, CANCELLED, COMPLETED, EXPIRED (mirrors Razorpay) |

**Competency applicability per mode** (rubric decides weights; weight 0 = N/A, shown as "—" in the report):

| Competency | DSA | LLD | Behavioural | Resume |
|---|---|---|---|---|
| PROBLEM_SOLVING | 0.30 | 0.20 | 0.25 | 0.20 |
| CODE_QUALITY | 0.20 | 0.15 | — | 0.15 |
| COMMUNICATION | 0.20 | 0.20 | 0.45 | 0.30 |
| DESIGN | 0.10 | 0.35 | — | 0.25 |
| EDGE_CASES | 0.20 | 0.10 | 0.30 (as "Result & impact") | 0.10 |

Behavioural also stores S/T/A/R sub-scores per answer in `evaluations.per_question`. These roll up into
PROBLEM_SOLVING (Task + Action), COMMUNICATION (structure and clarity) and EDGE_CASES, which the behavioural
rubric relabels as "Result & impact".

## 6. Indexes and constraints

| Table | Index / constraint | Purpose |
|---|---|---|
| users | `UNIQUE(email)` (citext), `UNIQUE(google_sub) WHERE google_sub IS NOT NULL` | login lookup |
| refresh_tokens | `UNIQUE(token_hash)`, `INDEX(user_id)`, `INDEX(family_id)` | refresh, family revoke |
| resumes | `INDEX(user_id, created_at DESC)`, `UNIQUE(user_id) WHERE is_active` | latest active resume |
| questions | `INDEX(type, status, difficulty)` | session question selection |
| questions | `HNSW (embedding vector_cosine_ops) WITH (m=16, ef_construction=64)` | similar-question search |
| questions | `INDEX USING GIN (to_tsvector('english', title \|\| ' ' \|\| body_md))` | admin and user search |
| question_tags | `INDEX(tag_id)` | filter by tag |
| question_hints | `UNIQUE(question_id, level)`, `CHECK(level BETWEEN 1 AND 3)` | graded hints |
| test_cases | `INDEX(question_id, is_sample, position)` | run samples vs all |
| rubrics | `UNIQUE(key, version)`, partial `UNIQUE(key) WHERE is_active` | one active version per mode |
| interview_sessions | `INDEX(user_id, created_at DESC)` | history list |
| interview_sessions | `INDEX(status, last_activity_at) WHERE status = 'IN_PROGRESS'` | inactivity sweeper |
| session_questions | `UNIQUE(session_id, position)` | ordering |
| turns | `UNIQUE(session_id, seq)`, `UNIQUE(session_id, client_message_id)` | ordering + idempotency |
| code_submissions | `INDEX(session_question_id, created_at)` | latest run |
| evaluations | `UNIQUE(session_id)` | one evaluation per session |
| competency_scores | `UNIQUE(evaluation_id, competency)`, `INDEX(user_id, competency, created_at)` | dashboard trends |
| topic_stats | `INDEX(user_id, avg_score)`, `INDEX(user_id, sr_due_at)` | weak topics, revision queue |
| ai_call_logs | `INDEX(user_id, created_at)`, `INDEX(session_id)`, `BRIN(created_at)` | budget queries, analytics |
| subscriptions | `UNIQUE(provider_subscription_id)`, `INDEX(user_id, status)` | webhook lookup |
| payment_events | `UNIQUE(provider_event_id)` | webhook idempotency |
| shared_reports | `UNIQUE(token_hash)` | public lookup |
| all score columns | `CHECK (score BETWEEN 0 AND 10)` / `CHECK (overall_score BETWEEN 0 AND 100)` | integrity |

## 7. Modelling decisions

1. **Type-specific detail tables** (`dsa_problems`, `lld_problems`, `behavioural_prompts`) instead of one wide
   nullable table or pure JSONB. Common fields are queryable and indexed, specific fields are validated, and
   adding a new question type adds a new table without breaking existing ones.
2. **Rubrics versioned as immutable rows** with `definition jsonb` validated against the `RubricDefinition` record (Bean Validation + weight-sum check) on write. Sessions
   pin `rubric_id`, so historical reports remain reproducible after a rubric change.
3. **Overall score is never trusted from the LLM.** It is computed in code as `Σ(weight × score) / Σ(weight) × 10`,
   and only applicable competencies count.
4. **Denormalised `user_id` and `mode` on `competency_scores`** so the dashboard trend query reads one index
   without joining sessions → evaluations.
5. **Turns are append-only** with a per-session `seq`. The transcript is the audit trail used by evaluation.
6. **pgvector**: the entity maps `embedding` with `hibernate-vector` (`@JdbcTypeCode(SqlTypes.VECTOR) @Array(length = 1024) float[]`).
   Similarity search is a parameterised native query (`<=>` cosine) in `QuestionEmbeddingRepository`, the only place
   that touches vector SQL.
7. **Soft delete** for users and questions (archive). **Hard delete** for resumes on user request (privacy).
   Deleted users are also **anonymised** (email rewritten to `deleted+<id>@deleted.invalid`, name, password
   hash and Google subject cleared), so the original address can register again and no personal data remains.
8. **Enums are `text` + `CHECK` constraints, not PostgreSQL `ENUM` types** (implemented in Phase 2). Adding a
   value is a constraint change inside a normal transactional migration, and values can be removed. PG enums
   allow neither cleanly. The diagrams keep enum names for readability.
9. **`refresh_tokens.ip` is `text` (≤ 45 chars, fits IPv6)** rather than `inet`: it is only displayed and
   audited, never range-queried, so the simpler mapping wins.
10. **Optimistic locking**: `users.version` (JPA `@Version`) guards concurrent admin edits.
