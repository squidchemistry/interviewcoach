# AI Interview Coach

Mock interviews with an AI interviewer for software-engineering candidates. It covers DSA (with sandboxed code
execution), Low-Level Design, Behavioural (STAR) and Resume deep-dive rounds, and returns rubric-based,
evidence-backed feedback with progress tracking.

> **Status: v1.0.0, feature complete.** All four interview rounds, streaming AI interviewer, rubric-based
> evaluation with verified evidence, dashboard, spaced-repetition revision, resumes, Pro billing (Razorpay), share
> links and the admin panel work end to end. Everything runs **offline with zero paid keys**: AI, code execution
> and payments have clearly labelled mock modes.

## Tech stack

| Layer | Choice |
|---|---|
| Backend | **Java 25 LTS · Spring Boot 3.5** (Spring MVC on virtual threads) · Spring Security (JWT resource server) · Spring Data JPA · Flyway · springdoc-openapi |
| Frontend | **React 19 · Vite 7 · TypeScript 5.9** · Tailwind CSS 4 · TanStack Query · React Router · types generated from the API's OpenAPI document |
| Data | PostgreSQL 16 + **pgvector** · Redis 7 |
| AI | Provider Strategy: Claude / OpenAI-compatible (incl. Gemini) / **Mock** (zero paid keys) · JMustache prompt files · pgvector embeddings |
| Code execution | Judge0 CE (sandboxed) or a labelled mock executor · Monaco editor |
| Payments | Razorpay Subscriptions (webhook-driven) or a labelled mock |
| Infra | Docker Compose · GitHub Actions · nginx |
| Tests | JUnit 5 · Mockito · AssertJ · **Testcontainers** · WireMock · ArchUnit · Vitest · Testing Library · MSW · **Playwright** |

Reasons and trade-offs for each choice: [docs/02-architecture.md](docs/02-architecture.md).

## Quick start (Docker only)

Requirements: Docker Desktop (or Docker Engine + Compose v2).

```bash
cp .env.example .env               # Windows: copy .env.example .env
docker compose up --build -d --wait
```

| What | URL |
|---|---|
| Web app | http://localhost:8088 |
| API health | http://localhost:8080/api/v1/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

**Demo accounts** (created on startup because `.env.example` sets `SEED_DEMO_DATA=true`; local use only):

| Account | Email | Password |
|---|---|---|
| Candidate (SDE-1, with profile) | `demo@aicoach.dev` | `DemoPass2026` |
| Admin | `admin@aicoach.dev` | `AdminPass2026` |

If a port is already in use on your machine, change `WEB_PORT` / `API_PORT` in `.env`.

### Optional: Google sign-in
Create an OAuth client (type *Web application*) in the Google Cloud console. Add
`http://localhost:8088/api/v1/auth/google/callback` as an authorised redirect URI, then set `GOOGLE_CLIENT_ID` and
`GOOGLE_CLIENT_SECRET` in `.env` and restart. The "Continue with Google" button appears only when these are
configured.

## Local development

Requirements: **JDK 25** (e.g. `winget install EclipseAdoptium.Temurin.25.JDK`), **Node 22+**, Docker.

```bash
docker compose up -d postgres redis          # infrastructure only
cd backend && ./mvnw spring-boot:run         # API on :8080 (profile `local` reads ../.env automatically)
cd frontend && npm install && npm run dev    # web on :5173, proxies /api to :8080
npm run gen:api                              # regenerate frontend API types after changing backend DTOs
```

## Testing

```bash
cd backend && ./mvnw verify      # unit (*Test) + integration (*IT, Testcontainers) + ArchUnit + JaCoCo ≥ 80% lines
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
./infra/scripts/verify.sh        # everything above + docker compose up + smoke tests (PowerShell: .\infra\scripts\verify.ps1)
cd frontend && npx playwright install chromium && npm run e2e   # E2E against the running compose stack (:8088)
```

Integration tests start real PostgreSQL (pgvector) and Redis containers, so Docker must be running. Google
sign-in is tested against a WireMock "Google" that signs real RS256 ID tokens.

## Configuration

All configuration comes from environment variables, documented in [.env.example](.env.example). The backend
validates its configuration at startup and refuses to boot when a value is missing or invalid (for example a
wildcard CORS origin or a JWT secret shorter than 32 bytes). Never commit `.env`.

| Variable | Purpose | Default |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database credentials | — (required) |
| `REDIS_PASSWORD` | Redis auth | — (required) |
| `JWT_SECRET` | HS256 key for access tokens (base64, ≥ 32 bytes; `openssl rand -base64 48`) | — (required) |
| `COOKIE_SECURE` | `Secure` flag on the refresh cookie (false only for plain-http localhost) | `true` |
| `CORS_ORIGINS` | Exact origins allowed to call the API with credentials | `http://localhost:5173,http://localhost:8088` |
| `WEB_BASE_URL` | Where Google sign-in returns the browser | `http://localhost:8088` |
| `SEED_DEMO_DATA`, `DEMO_USER_PASSWORD`, `ADMIN_PASSWORD` | Demo accounts on startup (never in production) | off |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `GOOGLE_REDIRECT_URI` | Google sign-in (all three enable it) | unset (disabled) |
| `HTTP_MAX_BODY_BYTES` | Max JSON request body | `102400` |
| `WEB_PORT` / `API_PORT` | Host ports for docker compose | `8088` / `8080` |
| `AI_PROVIDER`, `AI_API_KEY`, `AI_BASE_URL` | `mock` \| `anthropic` \| `openai` (OpenAI-compatible) | `mock` |
| `AI_MODEL_FAST` / `AI_MODEL_SMART` | Interviewer turns / evaluation and extraction | `claude-haiku-5-5` / `claude-sonnet-5-5` |
| `AI_SESSION_TOKEN_BUDGET` / `AI_DAILY_TOKEN_BUDGET` | Token budgets per interview / per user per day | `80000` / `400000` |
| `AI_EMBEDDING_PROVIDER`, `AI_EMBEDDING_API_KEY` | `mock` (hashing) \| `openai` for "similar questions" | `mock` |
| `CODE_EXECUTOR`, `JUDGE0_URL`, `JUDGE0_AUTH_TOKEN` | `mock` (code not run, labelled) \| `judge0` | `mock` |
| `PAYMENTS_PROVIDER`, `RAZORPAY_*` | `mock` (test-mode button) \| `razorpay` | `mock` |
| `FREE_MONTHLY_INTERVIEWS` | Free-plan interviews per month | `3` |
| `SPRING_PROFILES_ACTIVE=prod` | Refuses to start with mock code execution or payments | unset |
| `JUDGE0_*` (compose) | Judge0 sandbox credentials (`--profile judge0`) | — |

## What's implemented

### v1.0.0: interviews, evaluation, progress, billing
- **Four rounds**:
  - **DSA**: Monaco editor in Python, Java, C++ or JavaScript; Run on sample tests and Submit on hidden tests;
    hints in three levels, which cost score.
  - **Low-Level Design**: a Mermaid class-diagram editor with live preview.
  - **Behavioural**: STAR-aware follow-ups.
  - **Resume deep-dive**: questions generated about *your* named projects.
- **AI interviewer** streamed token by token over SSE. Events go through Redis Streams, so a reconnect replays
  exactly what was missed, from any replica. Follow-ups are grounded in the candidate's words.
- **Adaptive difficulty**: harder after answers scoring ≥ 7.5, easier at ≤ 4.0, with the reason shown.
- **State-machine engine** (State pattern) with optimistic locking, idempotent answers, retry and recovery from
  failed generations, plus a sweeper that handles stuck, idle and stalled sessions.
- **Evaluation** against versioned rubrics. The model scores criteria only; code computes the totals:
  - evidence quotes must be verbatim candidate text, or they are dropped;
  - competencies with no evidence are capped and marked low-confidence;
  - DSA blends in test pass rates and deducts hint penalties.

  The report has model answers, STAR breakdowns, three next steps, and integrity notes shown as context only.
- **Progress**: a dashboard with score history and competency trends (inline SVG with accessible tables), weak
  topics, and an **SM-2 spaced-repetition** revision queue.
- **Resumes**: PDF upload checked by magic bytes, ≤ 5 MB and ≤ 10 pages. PDFBox extracts the text and AI builds a
  structured profile. Skills and projects not found in the text are dropped. Users can correct the profile, and
  deleting a resume hard-deletes it.
- **Question bank**:
  - 30 DSA, 10 LLD and 23 behavioural questions, plus 3 company packs;
  - full-text search and filters;
  - "similar questions" via pgvector HNSW;
  - admin CRUD with per-type JSON validation;
  - rubric versioning and activation;
  - an AI usage report.
- **Plans**: the free plan gives 3 interviews a month. Pro (₹499/month) adds voice interviews (Web Speech API),
  Pro packs and unlimited interviews. Razorpay Subscriptions activate only from HMAC-verified, idempotent webhooks.
- **Share links**: anonymised public reports, stored as token hashes. They are revocable, `noindex` and `no-store`.
- **AI layer**: Decorator chain (metering and budgets → cache → circuit breaker → retry with jitter → vendor).
  Prompts are versioned files with untrusted-input fencing. Structured output is validated and repaired. The mock
  provider is deterministic.
- **Neo-brutalist UI**: cream paper, heavy ink borders, hard shadows and Space Grotesk/Mono type. It has dark mode,
  is responsive, and keyboard and screen-reader friendly. Heavy routes (Monaco, Mermaid, admin) are lazy-loaded.

### Phase 2: accounts and access (v0.2.0)
- **Email/password accounts** with Argon2id hashing, case-insensitive unique emails, and server- plus
  client-side validation (10+ characters, a letter and a digit).
- **Sessions**: 15-minute HS256 access tokens held **in memory only**, plus a 7-day opaque refresh token in an
  `HttpOnly; SameSite=Strict; Path=/api/v1/auth` cookie, stored server-side as a SHA-256 hash. Every refresh
  **rotates** the token. Replaying an old token revokes the whole session family, with a 30-second grace window
  for two tabs refreshing at once. The SPA refreshes single-flight and retries the failed request.
- **Google sign-in** (OIDC authorization code + **PKCE** + one-time `state` in Redis). ID tokens are verified
  against Google's keys, issuer and audience. Existing accounts are linked only when Google reports the email as
  verified. No token ever appears in a URL.
- **Roles**: `USER` / `ADMIN`. `/api/v1/admin/**` is admin-only, and a test enumerates every admin route and
  checks it is forbidden to users. Role changes are **audited** (`audit_logs`, with request id) and take effect
  at the next token refresh.
- **Profile and onboarding**: target role (SDE-1/SDE-2), experience in 0.5-year steps, up to 10 target companies,
  and preferred language.
- **Account deletion**: personal data is anonymised, every session revoked, and the email freed (an Observer
  event decouples the user and auth features).
- **Abuse protection**: Redis token-bucket rate limits (per IP for auth, per user for the API, with `RateLimit-*`
  and `Retry-After` headers), plus a login lockout after 5 failures per email+IP in 15 minutes. Unknown emails
  take the same time as wrong passwords, so timing doesn't reveal which accounts exist.
- **Web**: login, register, Google completion, onboarding, profile (with account deletion), admin user search
  and role management. Route guards, open-redirect-safe `?redirect=` handling, and accessible forms with
  `aria-describedby` errors.

### Phase 1: foundation (v0.1.0)
- RFC 7807 `problem+json` errors with stable `code` and `requestId` everywhere (including filter-level errors),
  request-id propagation, body-size limits, strict security headers, and actuator on an unpublished management port.
- `GET /api/v1/health` (readiness of DB, Redis and pgvector; 503 when degraded), Prometheus metrics and JSON logs.
- Architecture rules as tests: layering, transaction boundaries, no cross-feature repository access, and no
  `ProcessBuilder`/`Runtime.exec`/script engines (candidate code can never run on the API host).
- Docker images (multi-stage, non-root), compose healthchecks, and CI (hygiene, gitleaks, backend, frontend,
  compose smoke tests, generated-types freshness).

## Known issues

- **Judge0 doesn't run on Docker Desktop/WSL2 hosts with cgroup v2** ([R6](docs/09-risks.md)). The default
  executor is therefore a mock: it never runs code, and the UI and the evaluator both say that correctness was not
  measured. For real execution, use a cgroup-v1 VM or RapidAPI ([deploy guide](docs/deploy.md#5-judge0-real-code-execution)).
  The drivers are verified locally by `HarnessRoundTripTest`: all 30 reference solutions pass.
- Background work (AI generation, evaluation) runs in-process on virtual threads, plus a recovery sweeper, rather
  than on a durable queue. See the [as-built notes](docs/02-architecture.md#7-as-built-v100-deviations-from-this-design).
- The Monaco chunk is ~2.7 MB (≈ 710 kB gzipped). It is loaded only in coding rounds.
- ESLint is pinned to 9.x because `eslint-plugin-jsx-a11y` does not support ESLint 10 yet.
- A changed role or a deleted account keeps working with an already-issued access token until it expires
  (≤ 15 minutes). This is the standard trade-off of stateless JWTs; refresh is always checked against the database.

## Design documents

[PRD](docs/01-prd.md) · [Architecture](docs/02-architecture.md) · [Data model](docs/03-data-model.md) ·
[LLD](docs/04-lld.md) · [API spec](docs/05-api-spec.md) · [Folder structure](docs/06-folder-structure.md) ·
[Prompts](docs/07-prompts.md) · [Milestones](docs/08-milestones.md) · [Risks](docs/09-risks.md) ·
[Deploy](docs/deploy.md) · [Demo script](docs/demo-script.md) · [Talking points](docs/talking-points.md)

## Design decisions

- **Access token in memory, refresh token in an httpOnly cookie.** XSS cannot read either from storage. CSRF
  cannot use the access token (a header browsers never attach), and the cookie is `SameSite=Strict`,
  path-scoped and `Origin`-checked. A reload costs one `POST /auth/refresh`, which also returns the user.
- **Rotation with reuse detection and a grace window.** Detecting replay of a stolen refresh token is worth the
  complexity. The 30-second grace avoids logging out users who have two tabs open.
- **Rate limiting in Redis with a Lua script** rather than a library: one atomic round trip using the Redis
  server clock, so all API replicas agree. It fails open (readiness already reports Redis down). Login lockout is
  a separate per-email+IP counter, so an attacker cannot lock a victim out everywhere.
- **Enums as `text` + `CHECK` constraints** instead of PostgreSQL `ENUM` types: values can be added or removed in
  ordinary migrations.
- **OpenAPI → TypeScript types**: records are fully required and `@Nullable` components become `X | null`
  (OpenAPI 3.1 `anyOf`). CI fails if `schema.d.ts` is stale, so the frontend cannot drift from the API.
- **Observer for account deletion**: the user feature publishes `UserDeletedEvent`. The auth feature revokes
  tokens in the same transaction without the two features depending on each other's internals.
- **RFC 7807 everywhere, including in filters**, and a public health summary separate from internal actuator.
- **Build fixes found along the way**: Flyway pinned to 11.20.x (older versions find no migrations under paths with
  spaces), and the JaCoCo agent attached with a relative `destfile`. Both were caught by tests.
- **Model scores criteria, code computes totals** (`EvaluationSteps`, a Chain of Responsibility). The model never
  produces an overall score, so prompt changes cannot silently re-weight the rubric, and fabricated evidence is
  filtered out deterministically.
- **Effects after commit**: a state transition only *requests* AI work. The work runs after the transaction
  commits and reports back as an internal command, so no AI call ever happens while holding a database lock.
- **Honest mocks**: every mock (AI, code execution, payments) is labelled in the API and the UI. The `prod`
  profile refuses mock code execution and mock payments.
