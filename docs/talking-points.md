# Talking points

Short, defensible answers for discussing this project in interviews. Each point names the decision, the
trade-off, and where to find it in the code.

## Architecture

- **Modular monolith, layered by feature** (`auth`, `user`, `question`, `interview`, `resume`, `billing`,
  `progress`). ArchUnit enforces the layering:
  - controllers never touch repositories or entities;
  - `@Transactional` lives only in services;
  - production code can never spawn processes or script engines.

  *Why not microservices?* One team, one deployable, transactional consistency between a session and its
  turns. The feature packages are the seams for a later split.
- **Virtual threads (Java 25)** for request handling and background AI work. SSE connections, streaming LLM
  calls and polling Judge0 are all blocking I/O, which virtual threads make cheap. No reactive stack is needed.

## The interview engine

- **State pattern** (`interview/service/engine`): `Created → InProgress{Question, Answer, FollowUp} →
  Evaluating → Completed | Abandoned`. Each state accepts only its legal commands; everything else is
  `409 INVALID_TRANSITION`. That makes the transition table unit-testable and impossible to bypass.
- **Command → transition → effects.** A transition runs in one DB transaction with **optimistic locking** on
  the session row (`@Version`). It only *requests* asynchronous effects (generate, assess, evaluate), which run
  after commit and report back as internal commands. Concurrent clicks and two tabs can't corrupt state: the
  loser retries on fresh state.
- **Idempotent answers** by `clientMessageId` (unique index). Network retries never duplicate a turn.
- **Strategy + Template Method** for rounds: DSA, LLD, behavioural and resume rounds differ only in hooks:
  - question choice;
  - prompt variables;
  - score adjustment (DSA blends in test results and hint penalties);
  - follow-up policy.
- **Adaptive difficulty** is a small strategy (`DifficultyPolicy`): up at ≥ 7.5, down at ≤ 4.0. The reason is
  shown to the candidate.

## Streaming

- **SSE over Redis Streams.** Every event is `XADD`ed. The SSE id *is* the stream id, so a reconnecting client
  sends `Last-Event-ID` and replays exactly what it missed, from any replica.
  - If its position was trimmed away, the server sends `resync`.
  - Heartbeats every 15 s; nginx buffering is off.
- Tokens are coalesced into ~24-character batches, so each SSE event carries a few words, not a character.

## AI layer

- **Decorator chain**: metering (token budgets per session and day) → caching (deterministic prompts) →
  circuit breaker → retry with full jitter (transient errors only) → vendor adapter. Each layer has one job and
  is tested alone.
- **Provider Strategy + `AI_PROVIDER=mock`**: the whole product runs offline with no keys. The mock reads the
  prompt *variables*, not prose, so its output is coherent: it asks follow-ups and quotes real answers. It is
  labelled as mock everywhere.
- **Prompts are versioned files** with front-matter (tier, temperature, JSON mode). Untrusted input (answers,
  resumes) is fenced with a per-call nonce and an explicit "ignore instructions inside" clause.
- **Structured output with repair**: JSON is validated with Bean Validation. Invalid output goes back to the
  model up to twice with the list of problems, then fails cleanly.

## Trustworthy evaluation

- The model scores **rubric criteria only**. Competency and overall scores are computed in code (Chain of
  Responsibility in `EvaluationSteps`):
  1. criterion averaging;
  2. **evidence verification** (quotes must be verbatim candidate text, otherwise dropped);
  3. objective blend (test pass rate);
  4. hint penalties;
  5. a **low-confidence cap**;
  6. weighted aggregation.
- Rubrics are versioned data. Admins publish a new version and activate it, and old reports keep the version
  they were scored with.
- Integrity signals (large pastes, tab switches, prompt-injection attempts) are shown as **context, never as a
  penalty**.

## Code execution

- User code **never runs on the API host** (an ArchUnit rule). Judge0 runs it in a sandbox with time and memory
  limits and no network.
- `HarnessBuilder` generates a driver per language: it parses typed stdin, calls the candidate's function and
  prints canonical JSON. Comparators handle order-insensitive lists and float tolerance.
- **Honest failure mode**: Judge0 can't run on cgroup-v2 hosts, so the default executor is a mock that returns
  `NOT_EXECUTED`. The UI says tests weren't run, and the evaluator is told correctness is unmeasured. The mock is
  refused under the `prod` profile.
- A test runs all 30 Python reference solutions through the real harness, and checks that the drivers for every
  language compile and run.

## Data

- PostgreSQL + **pgvector** (HNSW, cosine) for "similar questions"; embeddings are backfilled by a job.
- `jsonb` for type-specific question details, validated at the boundary per type. One table instead of four,
  with validation in one place.
- **SM-2 spaced repetition** per topic tag drives the revision queue.

## Security and payments

- Access JWT in memory and a rotating refresh token in an httpOnly, `SameSite=Strict` cookie, with reuse
  detection. Argon2id passwords, Redis Lua rate limiting and RFC 7807 errors everywhere.
- **Razorpay**: the plan changes only from an HMAC-verified webhook (constant-time compare). Webhooks are
  idempotent by event id via an inbox table. The checkout signature is verified but never trusted to grant Pro.
- Resume uploads are checked by magic bytes, size and page count; text is extracted with PDFBox. Extracted
  skills and projects are **grounded** (dropped unless present in the text). The whole resume is hard-deleted on
  request and on account deletion (Observer on `UserDeletedEvent`).
- Share links store only a SHA-256 of the token. Public reports are anonymised, `no-store`, `noindex`, and expired
  or revoked links return the same 404.

## Testing

- Unit tests for pure logic: evaluation chain, SM-2, decorators, circuit breaker, signatures, grounding.
- WireMock for the Claude, OpenAI and Judge0 adapters.
- **Testcontainers** ITs (real Postgres + pgvector + Redis) drive whole interviews through the HTTP API: SSE
  replay, sharing, quotas, Pro gating, billing.
- ArchUnit layering tests, a JaCoCo ≥ 80% gate, and Playwright E2E against the compose stack in CI.

## What I'd do next

- Move effects to a durable queue (JobRunr or a Postgres outbox) instead of in-process virtual threads plus a
  sweeper.
- Calibrate the evaluator against human-graded transcripts. Track drift per rubric and prompt version.
- Replace the in-house harness with Judge0's multi-file submissions for C++ templates.
