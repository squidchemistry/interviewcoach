# 10 · Architecture and ER diagrams (as built, v1.0.0)

These diagrams are drawn from the shipped code: the Flyway migrations (`backend/src/main/resources/db/migration`),
the Java packages under `backend/src/main/java/com/aicoach`, `docker-compose.yml` and `frontend/`. The design-time
diagrams in [02-architecture.md](02-architecture.md) §2 and [03-data-model.md](03-data-model.md) are kept for the
reasoning, but where they differ (a separate JobRunr worker, object storage for resumes, per-type question tables),
the diagrams here show what actually runs. The differences are listed in [02-architecture.md](02-architecture.md) §7.

## 1. System architecture

```mermaid
flowchart LR
  subgraph Browser["Browser · React 19 SPA"]
    Pages["Pages: auth · interview room · report ·<br/>dashboard · revision · resumes · billing · admin"]
    Editors["Monaco editor (DSA) · Mermaid preview (LLD)"]
    SSEClient["SSE client<br/>(replays with Last-Event-ID)"]
  end

  subgraph Web["web container · nginx"]
    Static["Built SPA (static assets)"]
    Proxy["/api/ → api:8080<br/>(proxy_buffering off for SSE)"]
  end

  subgraph Api["api container · Spring Boot 3.5 · Java 25 · virtual threads"]
    Filters["Filters: request id · security headers · CORS ·<br/>body limit · JWT auth · Redis rate limit"]
    Controllers["REST controllers /api/v1/**<br/>+ SSE GET /sessions/{id}/stream"]
    Engine["InterviewEngine (State pattern)<br/>+ rounds: DSA · LLD · Behavioural · Resume (Strategy)"]
    Effects["EffectRunner: AI turns and evaluation<br/>run on virtual threads after commit"]
    Eval["EvaluationService (Chain of Responsibility)<br/>+ SM-2 spaced repetition"]
    AIG["AiGateway: versioned prompts →<br/>Metering → Cache → CircuitBreaker → Retry → provider"]
    Exec["CodeRunner + HarnessBuilder →<br/>CodeExecutor (Judge0 or Mock)"]
    Modules["Auth (JWT + rotating refresh, Google OIDC) · Questions ·<br/>Resumes (PDFBox) · Progress · Billing · Audit · Admin"]
    Jobs["@Scheduled jobs: InterviewSweeper (30 s) ·<br/>EmbeddingBackfillJob (30 s)"]
  end

  PG[("PostgreSQL 16 + pgvector<br/>system of record")]
  RD[("Redis 7<br/>rate limits · AI cache · Streams")]
  J0["Judge0 CE<br/>(optional compose profile)"]
  LLM["LLM provider<br/>Anthropic · OpenAI-compatible · Mock"]
  EMB["Embeddings<br/>OpenAI · Mock hashing"]
  GO["Google OAuth"]
  RZ["Razorpay<br/>(or Mock)"]

  Pages --> Static
  Pages -->|"JSON"| Proxy
  SSEClient <-->|"text/event-stream"| Proxy
  Proxy --> Filters --> Controllers
  Controllers --> Engine
  Controllers --> Modules
  Engine --> Effects
  Effects --> AIG
  Effects --> Eval
  Eval --> AIG
  Engine --> Exec
  Exec --> J0
  AIG --> LLM
  Modules --> AIG
  Modules --> EMB
  Engine --> PG
  Modules --> PG
  Eval --> PG
  Jobs --> Engine
  Jobs --> EMB
  Effects -->|"XADD events"| RD
  Controllers -->|"XREAD replay"| RD
  Filters -->|"token bucket (Lua)"| RD
  AIG -->|"cache"| RD
  Modules --> GO
  RZ -->|"webhook (HMAC)"| Proxy
  Modules --> RZ
```

How to read it:

- **One backend deployable.** All work runs inside the `api` container. Slow work (AI turns, evaluation, resume
  parsing) runs on a virtual-thread executor; interview effects start only after their transition has committed. Two
  `@Scheduled` jobs recover anything a
  crash left behind (`InterviewSweeper`) and fill in missing question embeddings (`EmbeddingBackfillJob`).
- **Postgres is the source of truth**; Redis holds only rebuildable state: rate-limit buckets, the AI response
  cache, and the per-session event streams that the SSE endpoint replays.
- **Every external service has a mock** (LLM, embeddings, code execution, payments), so the stack runs with no
  paid keys. The `prod` profile refuses to start with mock code execution or payments.

## 2. Containers (docker compose)

```mermaid
flowchart TB
  User(["Browser"])

  subgraph Default["docker compose up (default)"]
    web["web · nginx + built SPA<br/>host :8088 → 80"]
    api["api · Spring Boot<br/>host :8080 · management :8081 (not published)"]
    postgres[("postgres · pgvector/pgvector:pg16<br/>127.0.0.1:5432 · volume pgdata")]
    redis[("redis · redis:7-alpine<br/>127.0.0.1:6379 · volume redisdata")]
  end

  subgraph Judge0["--profile judge0"]
    j0s["judge0-server · 127.0.0.1:2358"]
    j0w["judge0-workers (privileged)"]
    j0db[("judge0-db · postgres:16-alpine")]
    j0r[("judge0-redis · redis:7-alpine")]
  end

  User --> web
  User -->|"Swagger, direct API"| api
  web -->|"/api/"| api
  api --> postgres
  api --> redis
  api -.->|"CODE_EXECUTOR=judge0"| j0s
  j0s --> j0db
  j0s --> j0r
  j0w --> j0db
  j0w --> j0r
```

Start order is enforced by health checks: `api` waits for healthy `postgres` and `redis`, and `web` waits for a
healthy `api` (readiness at `:8081/actuator/health/readiness`). Flyway migrates the schema when `api` starts.

## 3. Backend modules and dependencies

Each module is a package under `com.aicoach` with `web` → `service` → `repository` → `domain` layers. Arrows mean
"imports from", computed from the `import` statements in `src/main/java`. Every module except `health` and `event`
also uses `common` (configuration, errors, security, rate limiting, validation); those arrows are left out.

```mermaid
flowchart LR
  interview --> ai
  interview --> question
  interview --> execution
  interview --> resume
  interview --> user
  progress --> interview
  progress --> question
  progress --> ai
  progress --> user
  question --> ai
  question --> execution
  question --> audit
  execution --> question
  execution --> user
  resume --> ai
  resume --> event
  billing --> user
  billing --> audit
  auth --> user
  auth --> event
  user --> audit
  user --> event
  health
```

| Module | Responsibility |
|---|---|
| `auth` | Email/password and Google sign-in, JWT access tokens, rotating refresh tokens, login throttling |
| `user` | Account, profile, target companies, account deletion (publishes `UserDeletedEvent`) |
| `question` | Question bank, search, pgvector similar questions, rubrics, admin CRUD, AI usage report, seed data |
| `interview` | Session state machine, rounds, transcript, SSE streaming, evaluation, sweeper, share links |
| `execution` | Harness generation per language, output comparison, Judge0 or mock execution |
| `ai` | `AiGateway`, prompt registry, provider adapters, decorators, structured-output validation, embeddings, `ai_call_logs` |
| `resume` | PDF upload and validation, PDFBox extraction, AI parsing with grounding checks |
| `progress` | Dashboard totals, score history, competency trends, weak topics, revision queue |
| `billing` | Plans and quotas, Razorpay or mock subscriptions, webhook inbox |
| `audit` | Append-only audit log of admin and billing actions |
| `health` | Health endpoint and the pgvector health indicator |
| `common` | Config properties, error handling (RFC 7807), security config, Redis token-bucket rate limiter |

## 4. Interview session lifecycle

```mermaid
stateDiagram-v2
  [*] --> CREATED: POST /sessions
  CREATED --> IN_PROGRESS: start
  CREATED --> ABANDONED: abandon

  state IN_PROGRESS {
    [*] --> QUESTION
    QUESTION --> ANSWER: interviewer turn streamed
    ANSWER --> FOLLOW_UP: answer needs a follow-up
    ANSWER --> QUESTION: next question
    FOLLOW_UP --> ANSWER: follow-up streamed
  }

  IN_PROGRESS --> EVALUATING: all questions done, ended by user,<br/>30 min inactivity or AI budget used (≥ 1 answer)
  IN_PROGRESS --> ABANDONED: abandon, or ended with no answers
  EVALUATING --> COMPLETED: evaluation succeeded
  EVALUATING --> EVALUATING: evaluation failed → retry (sweeper re-runs stalled ones)
  COMPLETED --> [*]
  ABANDONED --> [*]
```

`status` and `stage` are columns on `interview_sessions`; a CHECK constraint keeps `stage` non-null exactly when the
status is `IN_PROGRESS`. Transitions use optimistic locking (`version`) with retry on conflict
(`SessionStates.java`, `InterviewEngine.java`).

## 5. Core flow: answer → streamed interviewer turn

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser
  participant N as nginx (web)
  participant A as api
  participant P as Postgres
  participant E as EffectRunner (virtual thread)
  participant G as AiGateway
  participant R as Redis Stream

  B->>N: GET /api/v1/sessions/{id}/stream (Last-Event-ID)
  N->>A: proxied, unbuffered
  A->>R: replay events after Last-Event-ID, then keep reading
  B->>N: POST /api/v1/sessions/{id}/answers {content, clientMessageId}
  N->>A: proxied
  A->>A: JWT · rate limit · validation · ownership
  A->>P: insert ANSWER turn + state transition (optimistic lock, idempotent by clientMessageId)
  A-->>B: 202 Accepted
  A->>E: effect submitted after commit
  E->>G: assess the answer, then stream the next interviewer turn
  G->>G: metering → cache → circuit breaker → retry
  loop token batches
    G-->>E: delta
    E->>R: XADD token event
    R-->>A: SSE handler reads the new entry
    A-->>B: event: token (id = stream entry id)
  end
  E->>P: save the full interviewer turn + next state
  E->>R: XADD turn.completed / state.changed
  A-->>B: event: turn.completed
```

If the browser reconnects, it sends the last event id and the server replays only what it missed; if that
position was trimmed from the stream, it sends `resync` and the client reloads the session from Postgres.

## 6. ER diagram

21 tables in five migrations (`V1`–`V5`, plus the repeatable `R__companies`). Conventions used throughout:
UUID primary keys from `gen_random_uuid()`, `timestamptz` timestamps, enumerations stored as `text` with CHECK
constraints (not PostgreSQL ENUM types), and `jsonb` for structured AI output and type-specific question detail.

### 6.1 Overview (keys and relationships)

```mermaid
erDiagram
  users ||--o| profiles : "has"
  users ||--o{ user_target_companies : "targets"
  companies ||--o{ user_target_companies : "targeted by"
  users ||--o{ refresh_tokens : "owns"
  refresh_tokens |o--o| refresh_tokens : "replaced by"
  users |o--o{ audit_logs : "actor"
  users |o--o{ ai_call_logs : "metered for"
  users |o--o{ questions : "created"
  users |o--o{ rubrics : "created"
  users ||--o{ interview_sessions : "takes"
  rubrics ||--o{ interview_sessions : "scores with"
  resumes |o--o{ interview_sessions : "deep-dive on"
  interview_sessions ||--o{ session_questions : "asks"
  questions |o--o{ session_questions : "source of"
  interview_sessions ||--o{ turns : "transcript"
  session_questions |o--o{ turns : "belongs to"
  session_questions ||--o{ code_submissions : "runs"
  users ||--o{ code_submissions : "submits"
  interview_sessions ||--o| evaluations : "evaluated by"
  rubrics ||--o{ evaluations : "version used"
  evaluations ||--o{ competency_scores : "breaks down into"
  users ||--o{ competency_scores : "trend"
  users ||--o{ topic_stats : "per topic"
  users ||--o{ usage_monthly : "quota"
  users ||--o{ resumes : "uploads"
  users ||--o{ subscriptions : "pays for"
  interview_sessions ||--o{ shared_reports : "shared as"
  users ||--o{ shared_reports : "created"

  question_packs {
    uuid id PK
    text_array question_slugs
  }
  payment_events {
    uuid id PK
    text event_id UK
  }
```

The overview shows only relationships; every table's columns are in §6.2–§6.5.

`question_packs` and `payment_events` have no foreign keys (see §6.6).

### 6.2 Identity, access and audit (`V2`)

```mermaid
erDiagram
  users ||--o| profiles : "has"
  users ||--o{ user_target_companies : "targets"
  companies ||--o{ user_target_companies : "targeted by"
  users ||--o{ refresh_tokens : "owns"
  refresh_tokens |o--o| refresh_tokens : "replaced_by_id"
  users |o--o{ audit_logs : "actor_id"

  users {
    uuid id PK
    citext email UK "unique, case-insensitive"
    text password_hash "null for Google-only accounts"
    text name
    text role "USER | ADMIN"
    text plan "FREE | PRO"
    text google_sub UK "partial unique"
    boolean email_verified
    timestamptz created_at
    timestamptz updated_at
    timestamptz deleted_at "soft delete, email anonymised"
    bigint version "optimistic lock"
  }
  profiles {
    uuid user_id PK, FK
    text target_role "SDE_1 | SDE_2"
    numeric experience_years "0-30, half-year steps"
    text preferred_language "PYTHON | JAVA | CPP | JAVASCRIPT"
    timestamptz onboarded_at
    timestamptz updated_at
  }
  companies {
    uuid id PK
    text name UK
    text slug UK
  }
  user_target_companies {
    uuid user_id PK, FK
    uuid company_id PK, FK
  }
  refresh_tokens {
    uuid id PK
    uuid user_id FK
    uuid family_id "rotation family, reuse detection"
    text token_hash UK "SHA-256, never the raw token"
    timestamptz expires_at
    timestamptz revoked_at
    uuid replaced_by_id FK
    text user_agent
    text ip
    timestamptz created_at
  }
  audit_logs {
    bigserial id PK
    uuid actor_id FK
    text action
    text entity_type
    uuid entity_id
    jsonb before
    jsonb after
    text request_id
    timestamptz created_at "BRIN index"
  }
```

### 6.3 Question bank and AI metering (`V3`)

```mermaid
erDiagram
  users |o--o{ questions : "created_by"
  users |o--o{ rubrics : "created_by"
  users |o--o{ ai_call_logs : "user_id"

  questions {
    uuid id PK
    text type "DSA | LLD | BEHAVIOURAL"
    text slug UK
    text title
    text body_md "full-text indexed with title"
    text difficulty "EASY | MEDIUM | HARD"
    text status "DRAFT | PUBLISHED | ARCHIVED"
    text_array tags "GIN index"
    text_array companies "GIN index"
    jsonb detail "type-specific: signature, tests, hints, ..."
    text source "SEED | ADMIN"
    vector embedding "1024-d, HNSW cosine index"
    text embedding_model
    timestamptz embedded_at
    uuid created_by FK
    bigint version "optimistic lock"
    timestamptz created_at
    timestamptz updated_at
  }
  question_packs {
    uuid id PK
    text slug UK
    text name
    text description
    text mode "DSA | LLD | BEHAVIOURAL"
    text company
    boolean pro "Pro-only pack"
    text_array question_slugs "refers to questions.slug"
  }
  rubrics {
    uuid id PK
    text key "DSA | LLD | BEHAVIOURAL | RESUME_DEEP_DIVE"
    int version "unique with key"
    boolean active "one active per key"
    jsonb definition
    text changelog
    uuid created_by FK
    timestamptz created_at
  }
  ai_call_logs {
    bigserial id PK
    text request_id
    uuid user_id FK
    uuid session_id "no FK"
    text purpose
    text provider
    text model
    text prompt_id
    text prompt_version
    int input_tokens
    int output_tokens
    int latency_ms
    boolean cache_hit
    text status "OK | ERROR | TIMEOUT | CIRCUIT_OPEN | BUDGET_EXCEEDED | INVALID_OUTPUT"
    text error_code
    timestamptz created_at "BRIN index"
  }
```

### 6.4 Interviews, evaluation and progress (`V4`)

```mermaid
erDiagram
  users ||--o{ interview_sessions : "user_id"
  rubrics ||--o{ interview_sessions : "rubric_id"
  resumes |o--o{ interview_sessions : "resume_id"
  interview_sessions ||--o{ session_questions : "session_id"
  questions |o--o{ session_questions : "question_id"
  interview_sessions ||--o{ turns : "session_id"
  session_questions |o--o{ turns : "session_question_id"
  session_questions ||--o{ code_submissions : "session_question_id"
  users ||--o{ code_submissions : "user_id"
  interview_sessions ||--o| evaluations : "session_id (unique)"
  rubrics ||--o{ evaluations : "rubric_id"
  evaluations ||--o{ competency_scores : "evaluation_id"
  users ||--o{ competency_scores : "user_id"
  users ||--o{ topic_stats : "user_id"
  users ||--o{ usage_monthly : "user_id"

  interview_sessions {
    uuid id PK
    uuid user_id FK
    text mode "DSA | LLD | BEHAVIOURAL | RESUME_DEEP_DIVE"
    text status "CREATED | IN_PROGRESS | EVALUATING | COMPLETED | ABANDONED"
    text stage "QUESTION | ANSWER | FOLLOW_UP, only while IN_PROGRESS"
    boolean processing "AI turn in flight"
    text target_role
    text company "no FK"
    text pack_slug "no FK"
    uuid resume_id FK
    uuid rubric_id FK "rubric version pinned"
    boolean voice_enabled
    smallint planned_questions "1-6"
    smallint current_index
    text current_difficulty
    bigint seed "deterministic choices (questions, mock AI)"
    jsonb integrity
    jsonb plan
    text end_reason
    bigint version "optimistic lock"
    timestamptz started_at
    timestamptz last_activity_at "sweeper index"
    timestamptz ended_at
    timestamptz created_at
    timestamptz updated_at
  }
  session_questions {
    uuid id PK
    uuid session_id FK "unique with position"
    smallint position
    uuid question_id FK "null for generated resume questions"
    text title
    text prompt_md
    text difficulty
    text difficulty_reason
    text_array tags
    jsonb context
    smallint hints_used "0-3"
    smallint follow_ups
    numeric turn_score "0-10"
    jsonb assessment
    timestamptz started_at
    timestamptz completed_at
  }
  turns {
    uuid id PK
    uuid session_id FK "unique with seq"
    uuid session_question_id FK
    int seq "SSE ordering"
    text role "INTERVIEWER | CANDIDATE | SYSTEM"
    text kind "QUESTION | ANSWER | FOLLOW_UP | HINT | CODE_RUN | SYSTEM_NOTICE"
    text content
    text format "TEXT | MARKDOWN | CODE | MERMAID"
    text input_mode "TEXT | VOICE"
    uuid client_message_id "idempotency, unique per session"
    jsonb metadata
    timestamptz created_at
  }
  code_submissions {
    uuid id PK
    uuid session_question_id FK
    uuid user_id FK
    text kind "RUN | SUBMIT"
    text language
    text source
    text verdict
    smallint passed
    smallint total
    jsonb results
    int runtime_ms
    text executor "judge0 | mock"
    timestamptz created_at
  }
  evaluations {
    uuid id PK
    uuid session_id FK, UK
    uuid rubric_id FK
    text status "PENDING | RUNNING | SUCCEEDED | FAILED"
    numeric overall_score "0-100"
    text confidence "HIGH | MEDIUM | LOW"
    text summary
    jsonb strengths
    jsonb gaps
    jsonb next_steps
    jsonb per_question
    jsonb integrity
    text provider
    text model
    text prompt_version
    smallint attempts
    text error
    timestamptz created_at
    timestamptz updated_at
    timestamptz completed_at
  }
  competency_scores {
    uuid id PK
    uuid evaluation_id FK "unique with competency"
    uuid user_id FK
    text mode
    text competency "PROBLEM_SOLVING | CODE_QUALITY | COMMUNICATION | DESIGN | EDGE_CASES"
    numeric score "0-10"
    numeric weight
    boolean low_confidence
    text rationale
    jsonb evidence "verified verbatim quotes"
    timestamptz created_at
  }
  topic_stats {
    uuid user_id PK, FK
    text tag PK
    int attempts
    numeric avg_score
    numeric last_score
    timestamptz last_seen_at
    smallint sr_repetitions "SM-2"
    numeric sr_ease "SM-2"
    int sr_interval_days "SM-2"
    timestamptz sr_due_at "revision queue index"
  }
  usage_monthly {
    uuid user_id PK, FK
    char period PK "YYYY-MM"
    int interviews_started "free-plan quota"
  }
```

### 6.5 Resumes, billing and sharing (`V5`)

```mermaid
erDiagram
  users ||--o{ resumes : "user_id"
  resumes |o--o{ interview_sessions : "resume_id"
  users ||--o{ subscriptions : "user_id"
  interview_sessions ||--o{ shared_reports : "session_id"
  users ||--o{ shared_reports : "created_by"

  resumes {
    uuid id PK
    uuid user_id FK
    text original_filename
    int size_bytes "max 5 MB"
    text sha256
    int page_count
    bytea content "PDF bytes, not mapped on the JPA entity"
    text status "UPLOADED | PARSING | PARSED | FAILED"
    text raw_text
    jsonb parsed
    jsonb integrity "ungrounded skills and projects flagged"
    text error
    boolean active "one active per user"
    timestamptz created_at
    timestamptz updated_at
  }
  subscriptions {
    uuid id PK
    uuid user_id FK
    text provider "RAZORPAY | MOCK"
    text provider_subscription_id UK
    text plan_id
    text status "CREATED | AUTHENTICATED | ACTIVE | PENDING | HALTED | CANCELLED | COMPLETED | EXPIRED"
    timestamptz current_end
    timestamptz created_at
    timestamptz updated_at
  }
  payment_events {
    uuid id PK
    text provider
    text event_id UK "webhook idempotency"
    text event_type
    jsonb payload
    timestamptz received_at
    timestamptz processed_at
    text processing_error
  }
  shared_reports {
    uuid id PK
    uuid session_id FK
    text token_hash UK "the link token is never stored"
    uuid created_by FK
    timestamptz expires_at
    timestamptz revoked_at
    int view_count
    timestamptz created_at
  }
  interview_sessions {
    uuid id PK
    uuid resume_id FK
  }
```

### 6.6 References without foreign keys

These columns point at other data by value on purpose; the database does not enforce them.

| Column | Points at | Why there is no FK |
|---|---|---|
| `interview_sessions.pack_slug` | `question_packs.slug` | Packs are reference data; a session keeps its slug even if the pack is renamed or removed. |
| `interview_sessions.company` | a company name | Free-text label for the session, also used with question `companies` tags. |
| `question_packs.question_slugs[]` | `questions.slug` | An array cannot carry a foreign key; slugs are resolved when a session is planned. |
| `questions.companies[]` | company names | Tags for filtering, indexed with GIN. |
| `ai_call_logs.session_id` | `interview_sessions.id` | Logs must survive session deletion; also used for per-session token budgets. |
| `payment_events.payload` | a subscription at the provider | Raw webhook inbox; processed idempotently by `event_id`. |

### 6.7 Notable constraints and indexes

| Table | Constraint or index | Purpose |
|---|---|---|
| `users` | `users_login_method` CHECK | A live account needs a password or a Google identity. |
| `rubrics` | partial unique `(key) WHERE active` | Exactly one active rubric version per round type. |
| `resumes` | partial unique `(user_id) WHERE active` | At most one active resume per user. |
| `interview_sessions` | CHECK `(status = 'IN_PROGRESS') = (stage IS NOT NULL)` | `stage` exists only while in progress. |
| `interview_sessions` | partial index on `(status, last_activity_at)` for in-progress and evaluating | The sweeper's scan. |
| `turns` | unique `(session_id, seq)` and partial unique `(session_id, client_message_id)` | Ordered transcript and idempotent answers. |
| `evaluations` | unique `session_id` | One evaluation per session. |
| `questions` | HNSW on `embedding`, GIN on `tags` / `companies` / full text | Similar questions, filters and search. |
| `audit_logs`, `ai_call_logs` | BRIN on `created_at` | Cheap time-range scans on append-only tables. |
