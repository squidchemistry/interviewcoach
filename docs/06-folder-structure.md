# 06 · Folder Structure

Two deployables in one repository: a **Spring Boot backend** (Maven, Java 25 LTS) and a **React frontend** (Vite,
npm). The same backend jar runs as the `api` (HTTP) and the `worker` (background jobs) through Spring profiles.

**Package-by-feature, layered inside each feature.** Each feature package has `web` (controllers), `dto`
(request and response records shared by web and service), `service`, `repository` and `domain` (JPA entities,
enums, value objects). Rules are
enforced by **ArchUnit tests** that run with `mvnw test`:
- `web → service → repository → domain`. Controllers never touch repositories or `@Entity` classes (DTOs only).
- A feature may use another feature's `service`, never its `repository` (ArchUnit-enforced for auth ↔ user ↔ audit).
- No `ProcessBuilder`, `Runtime.exec`, `javax.script` or `jdk.jshell` anywhere (user code runs only in Judge0).
- `@Transactional` is allowed only in `service` classes.

```
ai-interview-coach/
├── backend/
│   ├── pom.xml                              # Spring Boot 3.5.x parent, Java 25 LTS
│   ├── mvnw, mvnw.cmd, .mvn/wrapper/        # Maven Wrapper (no global Maven needed)
│   ├── Dockerfile                           # multi-stage: maven build → eclipse-temurin:25-jre (non-root, layered jar)
│   └── src/
│       ├── main/
│       │   ├── java/com/aicoach/
│       │   │   ├── AiCoachApplication.java
│       │   │   ├── common/
│       │   │   │   ├── config/              # AppProperties (@ConfigurationProperties, validated), Jackson, Clock, CORS, OpenAPI
│       │   │   │   ├── error/               # ErrorCode, AppException hierarchy, GlobalExceptionHandler (ProblemDetail)
│       │   │   │   ├── web/                 # RequestIdFilter (MDC), PageResponse, body-size limits
│       │   │   │   ├── security/            # SecurityConfig, JwtService, CurrentUser, rate-limit filter
│       │   │   │   ├── redis/               # RedisConfig, SessionLock, SessionEventStream (Redis Streams)
│       │   │   │   ├── storage/             # FileStorage (Strategy): LocalFileStorage, S3FileStorage
│       │   │   │   └── health/              # Judge0HealthIndicator, AiProviderHealthIndicator
│       │   │   ├── auth/                    # web, service (AuthService, TokenService, GoogleOAuthService), repository, domain
│       │   │   ├── user/                    # profile, usage, progress read models
│       │   │   ├── resume/                  # upload, PdfTextExtractor (PDFBox), ResumeParseJob
│       │   │   ├── question/                # catalogue + admin CRUD, QuestionEmbeddingRepository (native pgvector SQL)
│       │   │   ├── rubric/
│       │   │   ├── interview/
│       │   │   │   ├── web/                 # InterviewController, SessionStreamController (SseEmitter)
│       │   │   │   ├── engine/              # InterviewEngine (Facade), SessionContext, Transition, SessionCommand (sealed)
│       │   │   │   ├── state/               # State pattern: SessionState, BaseState, CreatedState … AbandonedState, SessionStateFactory
│       │   │   │   ├── round/               # Strategy: InterviewRound, BaseRound (Template Method), Dsa/Lld/Behavioural/ResumeDeepDiveRound, RoundFactory
│       │   │   │   ├── difficulty/          # DifficultyPolicy (Strategy), ThresholdDifficultyPolicy
│       │   │   │   ├── selection/           # QuestionSelector, SimilarQuestionRetriever (RAG)
│       │   │   │   ├── repository/
│       │   │   │   └── domain/
│       │   │   ├── execution/               # CodeExecutor (Strategy), Judge0CodeExecutor (Adapter), MockCodeExecutor, HarnessBuilder (Builder), comparators
│       │   │   ├── evaluation/              # EvaluationService, EvaluationPipeline + steps (Chain of Responsibility), EvaluateSessionJob
│       │   │   ├── progress/                # topic stats, SM-2 (Phase 2)
│       │   │   ├── ai/
│       │   │   │   ├── AiGateway.java       # Facade
│       │   │   │   ├── provider/            # LlmProvider (Strategy), Anthropic/OpenAi/Gemini adapters, mock/ (MockLlmProvider + scenarios), LlmProviderFactory
│       │   │   │   ├── decorator/           # Timeout, Retry, CircuitBreaker, Caching, BudgetGuard, Metering, Fallback
│       │   │   │   ├── embedding/           # EmbeddingProvider (Strategy) + OpenAI, Gemini, Voyage, MockHashing
│       │   │   │   ├── prompt/              # PromptRegistry, PromptTemplate (front-matter + JMustache)
│       │   │   │   ├── safety/              # UntrustedContentFramer, InjectionDetector
│       │   │   │   └── structured/          # StructuredOutputService, JSON Schema generation + validation
│       │   │   ├── billing/                 # Phase 2: PaymentProvider (Strategy), Razorpay adapter, webhook
│       │   │   ├── sharing/                 # Phase 2
│       │   │   ├── admin/                   # admin controllers composing feature services
│       │   │   ├── event/                   # domain event records + @TransactionalEventListener listeners (Observer)
│       │   │   └── job/                     # JobRunr job classes + recurring job registration (worker profile)
│       │   └── resources/
│       │       ├── application.yml          # common config
│       │       ├── application-local.yml    # host runs: imports ../.env (activated by `mvnw spring-boot:run`)
│       │       ├── application-api.yml      # (Phase 4) HTTP server, JobRunr background server off
│       │       ├── application-worker.yml   # (Phase 4) JobRunr background server on
│       │       ├── db/migration/            # Flyway: V1__extensions.sql, V2__auth.sql, …
│       │       ├── prompts/                 # versioned prompt templates (NOT inline strings)
│       │       │   ├── interviewer/{_shared,dsa,lld,behavioural,resume-deep-dive}.v1.md
│       │       │   ├── turn-assessment.v1.md, hint.v1.md, evaluation.v1.md, json-repair.v1.md
│       │       │   └── resume-extraction.v1.md, resume-question-generation.v1.md
│       │       └── seed/                    # JSON seed data (idempotent upsert by slug)
│       │           ├── dsa/*.json  lld/*.json  behavioural/*.json
│       │           ├── rubrics/*.json  tags.json  companies.json  users.json
│       │           └── demo-sessions.json
│       └── test/
│           ├── java/com/aicoach/
│           │   ├── architecture/            # ArchUnit layer + forbidden-API rules
│           │   ├── support/                 # IntegrationTest base (Testcontainers pgvector + Redis via @ServiceConnection), factories, auth helper
│           │   └── <feature>/               # unit tests (*Test) and integration tests (*IT)
│           └── resources/
│               ├── fixtures/                # resumes (incl. injection.pdf), transcripts, recorded LLM/Judge0 responses (WireMock)
│               └── golden/                  # evaluation calibration transcripts + expected score bands
│
├── frontend/
│   ├── package.json  package-lock.json
│   ├── Dockerfile                           # npm ci + build → nginx:alpine
│   ├── nginx/                               # default.conf + security-headers.conf: SPA fallback, /api proxy, SSE-safe (proxy_buffering off), security headers
│   ├── vite.config.ts  tsconfig.json  eslint.config.js  tailwind/postcss config
│   ├── playwright.config.ts
│   ├── e2e/full-interview.spec.ts
│   └── src/
│       ├── main.tsx
│       ├── app/                             # router, providers, layout, error boundary
│       ├── api/
│       │   ├── schema.d.ts                  # GENERATED from backend OpenAPI (openapi-typescript) — never hand-edited
│       │   ├── http.ts                      # fetch wrapper: bearer token, single-flight refresh, ProblemDetail errors
│       │   ├── sse.ts                       # fetch-event-source wrapper, Last-Event-ID, backoff
│       │   └── queries/                     # React Query hooks per resource
│       ├── features/
│       │   ├── auth/  onboarding/  resume/
│       │   ├── interview/ (NewSessionPage, InterviewRoom, useSessionStream, ChatTranscript, AnswerComposer, dsa/, lld/, voice/)
│       │   ├── report/  dashboard/  admin/  billing/  public-report/
│       ├── components/ui/                   # accessible primitives
│       ├── hooks/  lib/
│       └── test/                            # Vitest + RTL setup, MSW handlers
│
├── infra/
│   └── scripts/verify.ps1, verify.sh        # runs the per-phase verification commands
├── docs/
├── .github/workflows/ci.yml                 # backend (verify) · frontend (lint, typecheck, test, build) · docker build · e2e
├── docker-compose.yml                       # postgres, redis, api, worker, web (+ profile judge0)
├── .env.example
├── .editorconfig  .gitignore  .gitattributes
├── README.md
└── CHANGELOG.md
```

## Naming conventions

| Thing | Convention | Example |
|---|---|---|
| Java packages | `com.aicoach.<feature>.<layer>` | `com.aicoach.interview.state` |
| Classes | PascalCase with role suffix | `InterviewController`, `SessionRepository`, `DsaRound`, `AnswerState` |
| DTOs | Java `record`s, suffixed `Request` / `Response` | `CreateSessionRequest`, `ReportResponse` |
| DB tables / columns | snake_case, plural tables | `session_questions.turn_score` |
| Flyway migrations | `V<n>__<description>.sql` | `V3__question_bank.sql` |
| Env vars | SCREAMING_SNAKE, mapped to `app.*` properties | `AI_PROVIDER` → `app.ai.provider` |
| Prompt files | `<name>.v<N>.md` + front-matter `id`, `version` | `evaluation.v1.md` |
| Tests | `*Test` (unit, Surefire), `*IT` (integration, Failsafe), `*.test.ts(x)` (web), `*.spec.ts` (Playwright) | |
| Error codes | SCREAMING_SNAKE | `INVALID_TRANSITION` |
