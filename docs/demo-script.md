# Demo script (7–8 minutes)

A walkthrough for showing AI Interview Coach to an interviewer or reviewer. It runs entirely on the local Docker
stack with **no paid keys**: AI, code execution and payments use their clearly labelled mock modes.

## Before the demo (5 minutes, once)

```bash
cp .env.example .env
docker compose up --build -d --wait
```

- Open http://localhost:8088 in a fresh browser profile and sign in once as `demo@aicoach.dev` / `DemoPass2026` to
  warm the caches.
- Keep a second tab on Swagger UI (http://localhost:8080/swagger-ui.html) and an editor on
  `backend/src/main/java/com/aicoach/interview/service/engine/SessionStates.java`.
- Optional, for real AI output: set `AI_PROVIDER=anthropic` and `AI_API_KEY` in `.env`, then
  `docker compose up -d api`.

## 1. The pitch (30 s)

"Candidates rarely get realistic mock interviews with honest, specific feedback. This app runs four interview
types (coding, low-level design, behavioural and resume deep-dive) with an AI interviewer. It adapts the difficulty as
you go, and at the end it scores you against a versioned rubric. Every claim in the feedback is backed by a quote from what
you actually said."

## 2. Coding round (2 min)

1. **Practice → Coding (DSA)**, 1 question. Point out the free-plan meter ("1 of 3 this month").
2. The interviewer's first message **streams in token by token** (SSE through nginx).
3. Type an approach without code. The interviewer **follows up** on what you said, often on complexity.
4. Click **Hint** twice. Hints come in order: nudge, then approach. They are recorded and reduce the problem-solving
   score by the rubric's penalties.
5. In the editor, switch languages; the starter code comes from the same harness that wraps the judge.
   **Run** shows the sample tests. Mention that with `CODE_EXECUTOR=mock` the UI says plainly that the code was
   **not executed**. With Judge0 configured, the same flow runs the hidden tests in a sandbox.
6. Submit an answer. The **assessment toast** shows the turn score and why the next question gets harder or easier.

## 3. Report (1.5 min)

1. End the interview. The evaluation runs in the background, with progress shown over SSE.
2. Walk through the report:
   - overall score and confidence;
   - competency bars, with low-confidence markers;
   - **evidence quotes** linked to transcript turns, and the adjustments line, e.g. "−1.5 for 2 hints used";
   - three concrete next steps;
   - model answers per question.
3. Explain the trust story: the model only scores rubric *criteria*. Totals are computed in code. Quotes that
   aren't verbatim candidate text are dropped, and a competency without evidence is capped and marked low-confidence.

## 4. Behavioural + resume (1 min)

- Start a **Behavioural** round and give a short, vague answer. The follow-up targets the weakest STAR part
  (usually *Result*). The report shows a STAR breakdown.
- **Resume**: upload a PDF. The parsed profile appears within seconds. Skills that aren't literally in the PDF are
  dropped and listed as integrity notes. A resume deep-dive then asks about *your* named projects.

## 5. Progress and revision (45 s)

- **Dashboard**: score-over-time line, competency trends and weak topics (inline SVG with an accessible data
  table).
- **Revision**: an SM-2 spaced-repetition queue of topics with suggested questions. Weak topics come back sooner.

## 6. Pro, sharing, admin (45 s)

- **Billing**: upgrade in *test mode* (mock payments). The plan flips through the same code path a signed Razorpay
  webhook uses. Voice interviews and Pro packs unlock.
- **Share** a report: an anonymised public link (first name only, no transcript), revocable, `noindex`.
- **Admin** (`admin@aicoach.dev` / `AdminPass2026`): edit a question as JSON with per-type validation. Publish a new
  rubric version and activate it. View AI token usage by purpose and model.

## 7. Under the hood (1 min, show code)

- `SessionStates.java`: the **State pattern** interview engine. Every command goes through one `dispatch` with
  optimistic locking, and side effects run only after commit.
- `ai/service/AiConfig.java`: the **Decorator** chain (metering → caching → circuit breaker → retry → vendor).
  Swapping Claude for OpenAI or the mock is one env var.
- `HarnessRoundTripTest`: all 30 reference solutions pass through the generated drivers, and the drivers for every
  language are tested.
- CI: ArchUnit layering rules, Testcontainers ITs, an 80% coverage gate, a compose smoke test and Playwright E2E.

## Fallbacks if something breaks live

| Symptom | Recovery |
|---|---|
| Interviewer stuck "thinking" | The **Retry** button calls `/retry-turn`. The sweeper also releases stuck sessions after 2 minutes. |
| Evaluation failed | Click **Retry evaluation** on the report. |
| Quota hit | Promote the user in Admin, or use mock billing to go Pro. |
| Port clash | Change `WEB_PORT` / `API_PORT` in `.env`. |
