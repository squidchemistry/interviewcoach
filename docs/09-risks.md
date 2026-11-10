# 09 · Risks and Mitigations

Likelihood (L) and Impact (I): H/M/L. Each risk has an owner phase where its mitigation is built and tested.

| ID | Risk | L | I | Phase |
|---|---|---|---|---|
| R1 | LLM hallucinated or unstable scores | H | H | 3, 5, 8 |
| R2 | Prompt injection via resume or answers | H | M | 3, 5, 7 |
| R3 | Latency (time-to-first-token, evaluation wait) | M | M | 3, 5 |
| R4 | LLM cost overrun / abuse | M | H | 3, 9, 11 |
| R5 | Cheating (pasting AI answers, looking up solutions) | H | M | 5, 6 |
| R6 | Judge0 cannot run on this machine or in CI (cgroup v2) | **H (observed)** | H | 1, 6 |
| R7 | Judge0 hosting in production | H | M | 12 |
| R8 | LLM JSON malformed / schema drift | M | M | 3 |
| R9 | Provider outage or rate limiting | M | H | 3 |
| R10 | Race conditions in the session state | M | H | 5 |
| R11 | Resume PII exposure | L | H | 7, 9 |
| R12 | Web Speech API browser support | H | L | 10 |
| R13 | Razorpay webhooks unreachable in local dev | H | L | 11 |
| R14 | pgvector + JPA/Hibernate friction | M | L | 4 |
| R15 | Scope creep vs. placement timeline | H | H | all |
| R16 | Seed content quality (wrong tests or solutions) | M | H | 4 |

---

### R1 — LLM hallucinated or unstable scores
**Failure mode.** The evaluator gives 8/10 for an answer that missed the core idea, cites things the candidate
never said, or gives the same transcript 5 one day and 8 the next.

**Mitigations (layered; none relies on the prompt alone):**
1. **Rubric-anchored criterion scoring.** The LLM scores narrow criteria with explicit level descriptors and
   never computes totals. Totals are calculated in code from rubric weights.
2. **Evidence-or-penalty.** Each competency needs verbatim quotes from candidate turns, verified in code. Without
   surviving evidence the score is shrunk toward the midpoint and marked low-confidence in the UI.
3. **Objective signals override.** In DSA, test pass ratio caps correctness-related scores, and hints apply
   rubric penalties in code.
4. **Determinism.** Temperature 0 and a pinned model id per evaluation (stored). The rubric version is pinned
   per session.
5. **Calibration harness.** 12 golden transcripts with expected bands. `./mvnw -Pgolden verify` reports band accuracy
   and 3-run spread. This is run before changing a prompt or model, and the results go in the CHANGELOG.
6. **Optional self-consistency.** When confidence is LOW, a second sample is taken and the median used. This is
   cost-gated (Pro or config).
7. **Transparency.** The report shows the rubric version, the evaluator model, evidence quotes and
   "low confidence" badges, so a wrong score is visible instead of hidden.

### R2 — Prompt injection (resume text, answers, code comments)
**Mitigations.** Nonce-delimited `untrusted_data` framing with escape neutralisation. A system-level clause
tells the model to treat the block as data. **No tools or function-calling are exposed to the model**, so
there is nothing to exfiltrate or execute. Outputs are schema-validated and length-capped. Extracted resume
skills are grounded against the raw text. Generated questions are checked against real project names. An
injection detector raises integrity flags (visible, non-blocking). The adversarial fixture suite is in CI with
mock and run manually against real providers. The worst residual case is a skewed score for the attacker's own
session, which is self-limiting, since only their own report is affected.

### R3 — Latency
**Targets.** p95 time-to-first-token < 2.5 s for interviewer turns. Evaluation < 60 s p95.
**Mitigations.** Streaming everywhere. The `fast` tier (`claude-haiku-5-5`) handles interviewer turns, and the
`smart` tier only handles evaluation and extraction. Per-turn assessment runs *before* the follow-up
generation but is small (≤ 300 output tokens, temperature 0, cacheable). If it exceeds 4 s, the round falls
back to a rule-based decision (answer length + keyword coverage) so the conversation never stalls. Prompts are
kept compact (transcript summarised per completed question). The provider-side prompt cache is used for static
prefixes (rubric, system prompt) where the adapter supports it. Evaluation is asynchronous with progress events,
so the user is never blocked on a spinner without feedback.

### R4 — Cost overrun or abuse
**Mitigations.** Per-session token budget (stored on the session; the session ends gracefully when exhausted).
Per-user daily budget. Rate-limit classes. Free-tier monthly interview quota. Redis cache for identical
temperature-0 prompts. Model tiering. `maxTokens` per prompt. Admin AI-usage dashboard from `ai_call_logs`.
Mock mode for all development and CI. Abuse via multiple free accounts is mitigated by requiring email
verification before the second interview (Phase 9) and per-IP account-creation rate limits. The residual risk
is accepted for a portfolio project.

### R5 — Cheating
**Position.** Cheating is impossible to prevent in an unproctored practice tool, and detecting AI-written text
is unreliable. The design goal is *honest signals that help the candidate*, not punishment.
**Mitigations.** Client telemetry (large pastes, tab hidden durations) is stored and shown in the owner's report
as context. Follow-up questions probe understanding of what was pasted ("walk me through line 7"), which is
what real interviewers do. Per-turn time is recorded. Hidden test inputs are never sent to the client. Reference
solutions and model answers are never served before the session completes. The public share link shows
integrity notes only if the owner opts in (Phase 11).

### R6 — Judge0 does not run on this machine or in CI ⚠️
**Evidence.** `docker info` here reports **cgroup v2** (WSL2 kernel 6.6, Docker Desktop). Judge0 CE 1.13.x relies
on `isolate` with cgroup v1. Its documented setup requires cgroup v1, and GitHub's Ubuntu runners are also on
cgroup v2.
**Plan.**
1. **Phase 1 spike: done 2026-11-07, FAILED as predicted.** `docker compose --profile judge0 up` starts all four
   Judge0 containers and `GET /languages` returns 200, but every submission returns
   `status 13 Internal Error: "No such file or directory @ rb_sysopen - /box/script.py"`. This is the known
   symptom of `isolate` not being able to create its cgroup-v1 sandbox on a cgroup-v2 host. The ArchUnit rule (item 4)
   is already enforced, so nothing in the codebase depends on Judge0 until Phase 6.
2. **Decision needed before Phase 6.** Pick one (your call):
   - **(a)** Switch WSL2 to cgroup v1 via `%UserProfile%\.wslconfig` →
     `[wsl2] kernelCommandLine = systemd.unified_cgroup_hierarchy=0`, then `wsl --shutdown`. Community reports
     are mixed for the Docker Desktop VM, so this is not guaranteed. It also affects all WSL distros and may
     conflict with other tooling.
   - **(b)** Use **hosted Judge0 CE via RapidAPI** (free tier). `Judge0CodeExecutor` works unchanged with
     `JUDGE0_URL` and `JUDGE0_AUTH_HEADER`. The key goes in `.env` and is never committed.
   - **(c)** Run Judge0 on a small Linux VM with cgroup v1 (also the production answer, R7).
3. CI always uses `CODE_EXECUTOR=mock` (labelled; refuses to start with the `prod` profile). Real-Judge0 contract tests are
   tagged `@judge0` and run locally or on a self-hosted runner.
4. **Never** fall back to executing code on the API host. There is no `ProcessBuilder`, `Runtime.exec` or script-engine path, and an
   ArchUnit rule fails the build if any class uses them.

### R7 — Judge0 in production
Render, Railway and Vercel do not allow privileged containers.
**Mitigation.** Deploy Judge0 on a 2-vCPU VM (Docker, cgroup v1 kernel parameter, firewall allowing only the
API's egress IP, `AUTHN_TOKEN` set) or use RapidAPI hosted Judge0. Both are documented in `deploy.md`. If Judge0
is unreachable, DSA continues without execution (`503 CODE_EXECUTOR_UNAVAILABLE` path, graceful messaging).

### R8 — Malformed JSON / schema drift
Strict JSON Schema + Bean Validation, a repair loop (≤ 2), native JSON mode where available, job-level retries, and
`ai_call_logs.status = INVALID_OUTPUT` metrics. The schema is generated from the same Java record used by the
validator, so prompt and validator can never disagree.

### R9 — Provider outage or rate limits
Retry with backoff only for retryable errors, a circuit breaker per provider, an optional `AI_FALLBACK_PROVIDER`,
cached responses still served while the circuit is open, and graceful UI states (`AI_UNAVAILABLE`, retry-turn).
Evaluation jobs simply wait and retry.

### R10 — Race conditions in session state
Double-clicks, two tabs, retries after timeouts. Handled by a per-session Redis mutex, optimistic `version`
checks, `clientMessageId` uniqueness, a `jobId = sessionId` dedupe for evaluation, and transactional writes of
turn + state. Tested with concurrent-submission integration tests.

### R11 — Resume PII exposure
Files are stored under UUID names outside the web root and never served directly. Log masking (DTO `toString()` overrides plus a Logback masking pattern) is applied to
resume and transcript fields. Raw resume text is never logged or sent to analytics. Users can hard-delete.
Public share links redact PII. TLS only in production. LLM providers are configured for no training on API data
(the default for the API tiers used). This is documented in the README privacy section.

### R12 — Web Speech API support
`SpeechRecognition` is Chromium-only (Firefox lacks it). Feature detection disables the voice toggle with an
explanation. The optional server STT provider covers other browsers.

### R13 — Razorpay webhooks in local dev
Use `cloudflared tunnel` / `ngrok` to expose `/api/v1/webhooks/razorpay`. A `scripts/simulate-razorpay-webhook` script signs
sample payloads with the test webhook secret to exercise the handler offline. `PAYMENTS_PROVIDER=mock` allows
the full upgrade flow without Razorpay.

### R14 — pgvector with JPA/Hibernate
Hibernate 6.4+ maps vectors through the `hibernate-vector` module (`SqlTypes.VECTOR`), but HQL support for
distance operators is limited. All similarity queries are parameterised native SQL isolated in
`QuestionEmbeddingRepository`. The HNSW index
lives in a raw SQL migration, with an integration test against a real pgvector container.

### R15 — Scope creep vs timeline
This is the biggest *project* risk. **Mitigations.** Every phase is shippable and demoable on its own. The MVP
(Phases 1–9) is the portfolio core, and Phase 2 features are independent add-ons. If time runs short, the cut
order is: Phase 11 sharing → Phase 10 SRS → company packs → voice. **Never cut tests, the evaluation
reliability layer, or the README.**

### R16 — Seed content quality
Wrong test cases make the product look broken in a demo. **Mitigations.** Every DSA problem ships with a Python
reference solution, and the `@judge0` data test runs it against all tests. A publish validator enforces
completeness. The admin "validate tests" button covers new questions. LLD and behavioural content is
hand-curated with "what good looks like" guidance so the evaluator has ground truth.
