# Checkpoint 2: AI Interview Coach

Branch `demo-ready`, tag `checkpoint-2`. Nothing pushed yet.

## Repository recovery (before any code)

The local checkout was damaged: `.git/HEAD`, `.git/config` and the index were missing, and 63 tracked files (including `main.py` and the `Dockerfile`) were gone from the working tree. Steps taken:

1. Backed up the working tree and the old `.git` folder to the session scratchpad.
2. Re-initialised the git metadata in place, re-added `origin` (`https://github.com/squidchemistry/interviewcoach`) and fetched. `origin/main` = `ca2a247`.
3. Found uncommitted local work: an unfinished auth feature (JWT + Google OAuth). Its `models.py`, `service.py` and `database/session.py` existed only as `.pyc` files. It was committed unchanged to the **local-only branch `local-wip-auth`** (`0bcbc24`) so nothing is lost. It is not part of `demo-ready`.
4. Created `demo-ready` from `origin/main` and restored the missing files from git.

## What is done

**Persistence (SQLAlchemy 2.0 + Alembic), inside the clean-architecture layers**
- `app/domain/interview/entities.py`: `InterviewSession` aggregate (questions, answers, evaluations). It enforces the invariants (one answer per question, no answers after completion, auto-complete when every question is answered) and computes the summary. No framework imports.
- `app/application/interview/interfaces.py`: `AIService` and `InterviewSessionRepository` ports. `use_cases.py` adds create, get, list (paginated), submit answer and summary use cases. They return `Result` objects.
- `app/infrastructure/persistence/`: ORM models (`interview_sessions`, `questions`, `answers`, `evaluations`), the repository adapter, and a pooled engine (`pool_size`, `max_overflow`, `pool_pre_ping`, `pool_recycle`, connect timeout). `postgres://` URLs from Render are normalised.
- `migrations/versions/0001_initial_schema.py`: the initial migration. `answers.question_id` is UNIQUE, so two concurrent submissions of the same answer give one 201 and one 409.
- `app/infrastructure/seed.py`: an idempotent demo seed (3 sessions: completed, partial, unstarted), run on container start when `SEED_DEMO_DATA=true`.

**API**

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/sessions` | create a session; AI generates 1–10 questions (201) |
| GET | `/v1/sessions?limit=&offset=` | list, newest first; `limit` 1–100; returns `items/total/limit/offset` |
| GET | `/v1/sessions/{id}` | session with questions, answers, evaluations |
| POST | `/v1/sessions/{id}/answers` | evaluate and store one answer (201; 404 unknown; 409 already answered or completed) |
| GET | `/v1/sessions/{id}/summary` | average/best/worst score, strong/weak counts, top strengths/weaknesses |
| GET | `/health` | liveness (no dependencies) |
| GET | `/ready` | readiness: DB `SELECT 1`; 503 if down; reports the LLM provider |
| POST | `/v1/interview/generate-questions`, `/v1/interview/evaluate` | original stateless endpoints, kept for the current frontend |
| GET | `/ping` | legacy liveness alias |

Every error has the same shape: `{"error": {"code", "message", "details"}}`. This covers app errors, 422 validation (with a per-field list), 404/405 and unhandled 500s (no internals leaked). Logs are JSON lines. CORS comes from `CORS_ORIGINS`.

**LLM**
- `GroqAIService` takes an ordered `GROQ_MODELS` list (default `llama-3.3-70b-versatile,openai/gpt-oss-20b,llama-3.1-8b-instant`), a per-request timeout (`LLM_TIMEOUT_SECONDS`) and SDK retries with backoff (`LLM_MAX_RETRIES`). It falls back to the next model on a retired model, exhausted retries, an API error or unusable JSON. An auth error stops immediately. Scores are clamped to 1–10.
- `MockAIService` is deterministic and rule-based. `LLM_PROVIDER=auto` (the default) uses it when `GROQ_API_KEY` is empty. `LLM_PROVIDER=mock|groq` forces one.
- The Groq model IDs in the default list were not checked against Groq's current model list (no key was available). The fallback logic is tested with a scripted client.

**Docker**
- `Dockerfile` (API) is multi-stage: deps go into a venv in the builder stage, and the runtime copies only the venv and the code. pip/setuptools are removed, it runs as non-root, has a `HEALTHCHECK`, and `scripts/start.sh` runs `alembic upgrade head`, then the optional seed, then uvicorn.
- `frontend/Dockerfile` is multi-stage: a `node:24-alpine` build stage and an `nginx:1.27-alpine` runtime with SPA fallback (`frontend/nginx.conf`). The API URL is a build argument.
- `docker-compose.yml`: `db` (postgres:16-alpine, healthcheck, not published to the host), `api` (waits for a healthy db, port `${API_PORT:-8000}`), `web` (port `${WEB_PORT:-3000}`). `.env.example` lists every setting.

## Verification (commands run and results)

| Check | Command | Result |
|---|---|---|
| Lint | `.venv/Scripts/python -m ruff check .` | `All checks passed!` |
| Tests on SQLite | `.venv/Scripts/python -m pytest` | `47 passed` (3.6–5.6 s) |
| Tests on Postgres 16 | `TEST_DATABASE_URL=postgresql+psycopg://coach:coach@127.0.0.1:55432/coach_test pytest` (throwaway `postgres:16-alpine` container) | `47 passed in 10.60s` |
| Stack | `docker compose up -d` | db healthy, migration `0001` applied, `seed: created 3 demo session(s)`, api healthy |
| Smoke | curl against `localhost:8000` | `/ready` 200 `{"database":"ok","llm_provider":"mock"}`; list total 3; create 201; answer 201; duplicate answer 409; summary 200; unknown id 404 in the error shape; CORS preflight from `http://localhost:3000` allowed |
| Frontend | browser at `http://localhost:3000/interview` | page served by nginx (`/` and `/interview` both 200). Entering a role and experience produced 5 questions from the API (mock provider) |

The test suite covers domain rules, use cases with in-memory fakes, the Groq fallback (retired model, rate limit, bad JSON, auth error), the mock provider, settings parsing, a **layer-boundary test** (domain/application import no FastAPI/SQLAlchemy/Groq/infrastructure), a **migration-vs-models drift test**, a **concurrent duplicate-answer test** (two DB sessions, the unique constraint decides), the API on a migrated database, readiness 503, pagination, the error shapes and CORS.

## Image sizes (measured)

Measured with `docker image inspect <image> --format '{{.Size}}'` on Docker Desktop (engine 29.8.2, containerd image store). `docker image ls` shows the same values rounded.

| Image | Bytes | ≈ |
|---|---|---|
| Before: original `Dockerfile` at `ca2a247` (backend + built frontend in one image, `python:3.11-slim`) | 294,030,383 | 294 MB |
| Before: original `frontend/Dockerfile` (node:18 dev server) | did not build: `npm ci` fails with "Missing: yaml@2.9.1 from lock file" | n/a |
| After: API image, first multi-stage build | 352,949,737 | 353 MB |
| After: API image, pip + setuptools removed from the runtime venv | **315,924,175** | **316 MB** |
| After: frontend image (nginx + static build) | **101,371,351** | **101 MB** |

The API image is larger than the old combined image because it now ships the Postgres stack. Its venv layer is 101 MB, against 61.6 MB for the old `pip install` layer (from `docker history`). The largest new packages inside the image (`du -sh`) are SQLAlchemy 24 MB and psycopg-binary with its bundled libpq, about 21 MB. Multi-stage builds still mattered: they keep the build tooling out, and the frontend no longer ships inside the API image.

## Known gaps / notes

- **Leaked key in git history:** a `.env` with `GROQ_API_KEY` was committed in `e05a372` and `3a28624` (removed in `ca2a247`, but still in public history). **Revoke that Groq key.** History was not rewritten, because that needs a force push.
- `docs/architecture.md` was rewritten on disk by another process during this session, not by me. It is left **uncommitted** for its author or the user to review.
- No rate limiting yet (planned for checkpoint-3).
- The frontend still uses the stateless `/v1/interview/*` endpoints, and its results are not saved.
- `frontend/src/App.test.js` is the unmodified CRA template test ("learn react") and fails. Frontend tests are not written yet.
- `render.yaml` is outdated: no Postgres database, and `CORS_ORIGINS: "*"`. `vercel.json` + `api/index.py` (backend on Vercel) and `nixpacks.toml` (Railway) are leftovers. `README.md` and `DOCUMENTATION.md` are not updated.
- No CI yet.

## Checkpoint-3 needs

1. Frontend: a session history page (list with pagination, then detail and summary) on `/v1/sessions`; move the interview flow onto sessions so answers persist; API base URL from env; `frontend/vercel.json` (SPA rewrites); real frontend tests replacing `App.test.js`.
2. Rate limiting on the LLM-backed endpoints (per-IP; note the trade-off of in-memory vs shared store for a stateless tier).
3. `render.yaml`: Docker web service + managed Postgres (`fromDatabase` → `DATABASE_URL`), `healthCheckPath: /ready`, `GROQ_API_KEY` `sync: false`, CORS set to the Vercel URL. Remove `vercel.json`/`api/index.py`/`nixpacks.toml` if no longer used.
4. GitHub Actions: ruff, pytest (with a Postgres service and `TEST_DATABASE_URL`), frontend tests + build, docker build of both images, secret scan (gitleaks).
5. `DEMO.md` (3–5 min script and 10 interview Q&As) and a refreshed `README.md` (mermaid diagram, local run, deploy steps, the measured sizes above).
6. Push `demo-ready` to `origin`.
