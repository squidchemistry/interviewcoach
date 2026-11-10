# Changelog

All notable changes to this project are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/).

## [1.0.0] — 2026-11-10 — Phases 3–12: interviews, evaluation, progress, billing, neo-brutalist UI

### Added
- **AI layer**:
  - Provider Strategy (Anthropic, OpenAI-compatible, deterministic **mock**).
  - Decorator chain: metering with session and daily token budgets → cache → circuit breaker → retry with full
    jitter.
  - Versioned JMustache prompt files with nonce-fenced untrusted input.
  - Structured JSON output, validated with Bean Validation and repaired up to twice.
  - `ai_call_logs`.
- **Question bank** (`V3`): 30 DSA, 10 LLD and 23 behavioural questions, plus 3 company packs.
  - Full-text search and filters.
  - pgvector HNSW "similar questions", with an embedding backfill job.
  - Admin CRUD with per-type JSON validation and optimistic versioning.
  - Versioned rubrics (create and activate).
  - AI usage report.
- **Interview engine** (`V4`):
  - State pattern (`Created → InProgress{Question, Answer, FollowUp} → Evaluating → Completed | Abandoned`).
  - Optimistic locking with retry; effects run after commit on virtual threads.
  - Idempotent answers (`clientMessageId`).
  - Retry-turn; sweeper for stuck, idle and stalled sessions.
- **Rounds** (Strategy + Template Method):
  - DSA, with ordered hints and a 60/40 blend of AI judgement and test pass rate;
  - LLD, with a Mermaid diagram;
  - behavioural, STAR-aware;
  - resume deep-dive, with questions generated per project and curated fallbacks.
  - Adaptive difficulty (≥ 7.5 up, ≤ 4.0 down) with the reason shown.
- **Code execution**:
  - `HarnessBuilder` drivers (Python, Java, C++, JavaScript).
  - Comparators (exact, unordered list, float tolerance).
  - Judge0 batch executor and a labelled mock executor (`NOT_EXECUTED`; refused with the `prod` profile).
  - Run (samples) and Submit (hidden tests: verdicts only); 20 runs/min/user.
- **Streaming**: `GET /sessions/{id}/stream` SSE over Redis Streams. It replays from `Last-Event-ID` or `?after=`,
  sends `resync` when the position was trimmed, and sends heartbeats.
- **Evaluation**, a Chain of Responsibility:
  1. criterion scoring;
  2. verbatim-evidence verification (fabricated quotes dropped, wrong seqs corrected);
  3. objective blend;
  4. hint penalties;
  5. low-confidence cap;
  6. weighted aggregation and confidence.

  Topic statistics with **SM-2** spaced repetition. Retry when an evaluation fails.
- **Resumes** (`V5`):
  - PDF upload validated by magic bytes, ≤ 5 MB and ≤ 10 pages.
  - PDFBox extraction and background AI parsing.
  - Grounding: skills and projects not present in the text are dropped and flagged.
  - Corrections, activation, hard delete, and deletion on account deletion.
- **Progress**: `GET /me/usage`, `/me/progress` (totals, score history, competency trends, weak topics, recent
  sessions) and `/me/revision-queue`.
- **Billing**:
  - Free plan with 3 interviews a month; Pro at ₹499/month for voice, Pro packs and unlimited interviews.
  - Razorpay Subscriptions: HMAC-verified checkout and webhook, idempotent `payment_events` inbox, and a plan sync
    that is audited.
  - Labelled mock provider (`POST /billing/mock/complete`).
- **Sharing**: anonymised public reports (`/r/{token}`); token stored as SHA-256; revocable, `no-store`, `noindex`.
- **Frontend**: the whole UI restyled in **neo-brutalism**, with dark mode. New pages:
  - new interview;
  - interview room: SSE streaming, Monaco (local, four languages), Mermaid preview, hints, voice via the Web
    Speech API, integrity telemetry, evaluating overlay;
  - history and report: competency bars, evidence, STAR, model answers, share panel;
  - public report;
  - dashboard with SVG charts and accessible data tables;
  - questions browser and detail;
  - resume;
  - revision;
  - billing;
  - admin panel with users, questions, rubrics, AI usage and embeddings tabs.
- **Tests**: backend 199 unit + 65 integration tests, line coverage ≥ 80% (gate met). Highlights:
  - `HarnessRoundTripTest` runs all 30 reference solutions through the real drivers.
  - WireMock tests for the Claude, OpenAI and Judge0 adapters.
  - A state-machine transition table test.
  - End-to-end interview ITs covering SSE, share, quota and Pro gating; resume and billing ITs.

  Frontend: 74 tests. **Playwright E2E** (`npm run e2e`) runs register → onboarding → behavioural interview →
  report against the compose stack, and is also run in CI.
- **Docs**: [deploy guide](docs/deploy.md), [demo script](docs/demo-script.md),
  [talking points](docs/talking-points.md), and as-built deviations in
  [architecture §7](docs/02-architecture.md#7-as-built-v100-deviations-from-this-design).

### Changed
- The request body limit is 5 MB + 64 KB for `POST /api/v1/resumes` only; the global limit stays at 100 KB.
- The CSP allows Razorpay Checkout (script, frame and connect sources).
- Compose passes the AI, code-execution, payment and quota settings (all default to mock).
- The web image builds with `NODE_OPTIONS=--max-old-space-size=3072`.

## [0.2.0] — 2026-11-07 — Phase 2: auth, users, roles, profile

### Added
- **Database** (Flyway `V2__identity.sql`, repeatable `R__companies.sql`): `users`, `profiles`, `companies`,
  `user_target_companies`, `refresh_tokens`, `audit_logs`; 17 target companies as reference data.
- **Auth API**: `POST /auth/register`, `/login`, `/refresh`, `/logout`; `GET /auth/providers`; Google OIDC
  `GET /auth/google` and `/auth/google/callback` (authorization code + PKCE + one-time state in Redis).
- **Tokens**: HS256 access JWTs (15 min, `kid`, issuer/audience validated, 30 s clock skew) via Spring Security's
  resource server; opaque refresh tokens (7 days, SHA-256 hashed at rest) with rotation, family reuse detection
  and a 30 s multi-tab grace window; `HttpOnly; SameSite=Strict; Path=/api/v1/auth` cookie plus an `Origin` check.
- **Passwords**: Argon2id; timing-equalised login for unknown emails; lockout after 5 failures per email+IP for 15 minutes.
- **Rate limiting**: Redis Lua token bucket (`auth` 10/min/IP, `refresh` 30/min/IP, `api` 120/min/user) with
  `RateLimit-*` / `Retry-After` headers; fails open when Redis is unavailable.
- **Users API**: `GET /me`, `PUT /me/profile`, `DELETE /me` (anonymises the account, revokes all sessions via a
  `UserDeletedEvent` observer); `GET /companies`.
- **Admin API**: `GET /admin/users` (search, role filter, pagination; LIKE wildcards escaped) and
  `PATCH /admin/users/{id}/role` (cannot change own role; audited with request id).
- **Demo data seeder** (`SEED_DEMO_DATA`): demo candidate with a profile and an admin; idempotent; passwords from env.
- **OpenAPI**: bearer security scheme; records are fully `required`; `@Nullable` becomes OpenAPI 3.1 `anyOf` null.
- **Frontend**: in-memory session store with single-flight refresh and retry on 401; auth context and route
  guards; login, register, Google completion, onboarding, profile (with delete account), admin users pages;
  accessible form primitives; API types generated by `openapi-typescript` (`npm run gen:api`).
- **Tests**: backend 86 unit + 53 integration (auth flows, rotation and reuse, lockout, Google via WireMock with
  real RS256 ID tokens, admin route enumeration, rate limits, profile and deletion, OpenAPI contract); frontend 48
  (session refresh logic, every page, guards). Line coverage 93%.
- **CI**: checks generated API types are up to date; auth smoke test against the compose stack.

### Changed
- Unknown `/api/v1/**` routes now return 401 for anonymous callers (authenticated by default) and 404 once signed in.
- The `.env` import moved to a `local` profile (activated by `mvnw spring-boot:run`) so tests never read a
  developer's `.env`.
- `server.forward-headers-strategy=native` so rate limits see the real client IP behind nginx.
- Docs: rate limiting is a Lua token bucket (not Bucket4j); enums stored as text + CHECK; `refresh_tokens.ip` is text.

## [0.1.0] — 2026-11-07 — Phase 1: scaffold, infrastructure, CI, health

### Added
- **Backend** (`backend/`, Java 25, Spring Boot 3.5.16, Maven Wrapper):
  - RFC 7807 error handling (`GlobalExceptionHandler`, `ErrorCode`, `ProblemDetailFactory`) with `code` and
    `requestId` members, also used by filters and security handlers.
  - `RequestIdFilter` (validated incoming id or UUID, MDC, response header) and `RequestBodyLimitFilter`
    (413 for declared and streamed bodies).
  - Spring Security baseline: stateless, CORS whitelist, CSP / HSTS / frame / referrer headers, problem+json
    401/403, actuator permitted only on the management port.
  - `GET /api/v1/health` readiness summary (db, redis, pgvector, readinessState). `PgVectorHealthIndicator`.
  - Actuator liveness/readiness probes and Prometheus on management port 8081. springdoc Swagger UI.
  - Flyway `V1__extensions.sql` (vector, citext, pgcrypto). `ddl-auto=validate`. Virtual threads.
  - Validated `AppProperties` (`app.cors`, `app.http`). Local runs import `../.env`.
  - Tests: 42 unit tests (incl. ArchUnit rules) + 14 integration tests (Testcontainers pgvector + Redis, real
    management port). JaCoCo line-coverage gate ≥ 80% (currently 93%).
- **Frontend** (`frontend/`, React 19, Vite 7, TypeScript 5.9, Tailwind 4): accessible layout, home page, live
  system-status card, 404 page, typed fetch client that parses problem+json. 12 Vitest/RTL/MSW tests. ESLint with
  jsx-a11y.
- **Docker**: multi-stage non-root images (Temurin 25 JRE, nginx 1.28), `docker-compose.yml` with healthchecks for
  postgres, redis, api and web, plus an optional `judge0` profile. Configurable host ports.
- **CI**: GitHub Actions for hygiene (no TODO/FIXME, gitleaks), backend verify, frontend checks, and a compose smoke
  test.
- `infra/scripts/verify.ps1` / `verify.sh` one-shot verification.

### Changed
- Design docs updated for the Spring Boot backend and Java 25. The `worker` container moved to Phase 4, where the
  first background job exists.

### Fixed / worked around
- Flyway pinned to 11.20.3: Boot's managed 11.7.2 finds no migrations when the project path contains spaces.
- JaCoCo agent attached with a relative `destfile` (absolute path with spaces broke Surefire forking).
- Mockito loaded as a `-javaagent` (JDK 21+ dynamic-attach warning). Netty `sun.misc.Unsafe` allowed explicitly on
  JDK 25.

### Known issues
- Judge0 1.13.1 fails on cgroup-v2 hosts (spike result recorded in `docs/09-risks.md` R6). A fallback decision is
  needed before Phase 6.

## [0.0.0] — 2026-11-07 — Phase 0: planning

### Added
- Design documents in `docs/`: PRD, architecture, ER/data model, LLD, API spec, folder structure, prompt designs,
  milestone plan, risk register.
