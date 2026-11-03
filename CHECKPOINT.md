# Checkpoint 3: AI Interview Coach

Branch `demo-ready`, tag `checkpoint-3`. Pushed to `origin` (`https://github.com/squidchemistry/interviewcoach`) together with the tags `checkpoint-2` and `checkpoint-3`. Not deployed and not merged into `main`: those steps need the owner's accounts (see "Your remaining steps").

## Status against the definition of done

| Item | State |
|---|---|
| `docker compose up` (Postgres + API + web) | Done; verified with `--wait` and a smoke test |
| Passing tests | Done: 51 pytest (SQLite and Postgres 16), 11 Jest |
| GitHub Actions CI with a secret scan | Done: `.github/workflows/ci.yml`, validated with actionlint; first run happens on push |
| Health endpoints | Done: `/health` (liveness), `/ready` (database) |
| Seed / demo data | Done: 3 sessions, seeded on start when `SEED_DEMO_DATA=true` |
| `DEMO.md` | Done: 3–5 minute script and 10 Q&As; its curl snippets were run against the local stack |
| `README.md` with architecture diagram | Done (mermaid), plus `docs/architecture.md` |
| `render.yaml` / Vercel config | Done: `render.yaml` (API + managed Postgres), `frontend/vercel.json` |

## History

### Checkpoint 2 (`0351bf6`)

Postgres persistence (SQLAlchemy 2.0 + Alembic) inside the clean-architecture layers; the session API (create, list with pagination, get, answer, summary); `/health` and `/ready`; Groq model fallback with timeouts and retries plus a deterministic mock provider; a unique constraint on `answers.question_id` for concurrent submissions; the common error shape and JSON logs; multi-stage Docker images; and a compose stack. Before any code, the damaged local checkout was repaired, and the user's unfinished auth work was saved to the **local-only branch `local-wip-auth`** (not part of `demo-ready`, never pushed).

### After checkpoint 2

| Commit | Change |
|---|---|
| `2285b3f` | Per-IP sliding-window rate limit on the 4 LLM-backed endpoints (`RATE_LIMIT_PER_MINUTE`, default 20), 429 with `Retry-After` |
| `db41b76` | Frontend moved onto `/v1/sessions`: persisted runner that resumes after a refresh, `/history` with pagination, `/sessions/:id` results; real Jest tests; `frontend/vercel.json` |
| `c7078cb` | `render.yaml` Blueprint with managed Postgres and `/ready` health check; removed the backend-on-Vercel files, `nixpacks.toml` and `DOCUMENTATION.md` |
| this checkpoint | `docs/architecture.md` brought up to date (the uncommitted rewrite found on disk described the pre-`db41b76` frontend and removed files); stray `.pyc` files untracked; CI; `.gitleaksignore`; `DEMO.md`; README rewrite; removed `SOCIAL_MEDIA_CONTENT.md`, `RESUME_SKILLS_SECTION.md` (unmeasured metrics, old stack) and `frontend/UPDATE_SOCIAL_LINKS.md` (obsolete) |

## CI (`.github/workflows/ci.yml`)

Runs on pushes to `main` and `demo-ready`, on pull requests, and manually.

| Job | Steps |
|---|---|
| `backend` | Python 3.11, `pip install -r requirements-dev.txt`, `ruff check .`, `pytest` with `TEST_DATABASE_URL` pointing at a `postgres:16-alpine` service container |
| `frontend` | Node 22, `npm ci`, `npm test -- --watchAll=false`, `npm run build` (with `CI=true`, lint warnings fail the build) |
| `docker` | `docker build` of both images (no push), `docker compose up -d --build --wait`, curl `/ready`, list and create a session, fetch `/history` from nginx; logs on failure |
| `secret-scan` | gitleaks 8.30.1 binary (SHA-256 checked) over the full history (`fetch-depth: 0`) |

`.gitleaksignore` ignores exactly two findings: the Groq key in `.env` at `e05a372` and `3a28624`. That history is already public and can only be removed with a force push, so the key must be revoked instead. Any other finding fails the job.

## Verification (run 2026-11-03 in `C:\dev\AI-Interview-Coach`, after recreating `.venv` with Python 3.10.11)

| Check | Command | Result |
|---|---|---|
| Lint | `.venv/Scripts/python -m ruff check . --extend-exclude tools` | `All checks passed!` |
| Tests on SQLite | `.venv/Scripts/python -m pytest` | `51 passed` |
| Tests on Postgres 16 | `TEST_DATABASE_URL=postgresql+psycopg://coach:coach@127.0.0.1:55432/coach_test pytest` (throwaway `postgres:16-alpine` container) | `51 passed` |
| Frontend tests | `cd frontend && CI=true npx react-scripts test --watchAll=false` | `4 suites, 11 tests passed` |
| Frontend build | `cd frontend && CI=true npm run build` | `Compiled successfully.` |
| Workflow | `docker run --rm -v <repo>:/repo -w /repo rhysd/actionlint:1.7.7` | no findings |
| Secret scan | `docker run --rm -v <repo>:/repo zricethezav/gitleaks:v8.30.1 git /repo --redact` | `no leaks found` (2 known findings ignored; without the ignore file it reports exactly those 2) |
| Docker job, locally | both `docker build`s, `docker compose up -d --build --wait --wait-timeout 180`, then the CI curl smoke test | db, api, web all `healthy`; `/ready` → `{"database":"ok","llm_provider":"mock"}`; create 201; `/history` served by nginx |
| DEMO snippets | the concurrency and rate-limit loops from `DEMO.md` | one `201` + one `409`; `200`s then `429` with `retry-after` and `"code":"rate_limited"` |
| UI | browser at `http://localhost:3000/history` and `/interview` | history lists seeded and new sessions with progress and averages; setup form renders |

Ruff needs `--extend-exclude tools` locally only because of an untracked `tools/build_architecture_pdf.py` (not part of the repo); CI checks out tracked files only. The interview runner was not clicked through in the browser this time; it is covered by `InterviewRunner.test.js` and the API calls above.

Not verified: the GitHub Actions run itself (it starts with the push), anything on Render or Vercel, and Groq (no key was used; the default `GROQ_MODELS` IDs were not checked against Groq's current model list). No Docker image-size or build-time numbers are recorded, by choice.

## Your remaining steps

1. **Revoke the leaked Groq key** (committed in `e05a372` and `3a28624`) in the Groq console, then create a new one. Never put it in git.
2. **Check the first CI run** on GitHub → Actions for `demo-ready`.
3. **Render**: New → Blueprint → this repo. `render.yaml` deploys `main`; change `branch:` to `demo-ready` if you deploy before merging. Enter `GROQ_API_KEY` and, for now, `CORS_ORIGINS=http://localhost:3000`. Check `https://<service>.onrender.com/ready`.
4. **Vercel**: import the repo, Root Directory `frontend`, env var `REACT_APP_API_URL=https://<service>.onrender.com`, deploy.
5. **CORS**: set `CORS_ORIGINS` on Render to the Vercel URL (exact origin, no trailing slash).
6. **Merge `demo-ready` into `main`** with a pull request once CI is green; then add the live URLs to the README.

## Known limitations

- No authentication; every visitor shares one session history. The unfinished auth work is only on the local branch `local-wip-auth`.
- Rate-limit counters are per process (Redis is the multi-instance upgrade).
- LLM calls run inside the request; a job queue would be the next step at higher load.
