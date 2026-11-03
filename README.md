# AI Interview Coach

[![CI](https://github.com/squidchemistry/interviewcoach/actions/workflows/ci.yml/badge.svg?branch=demo-ready)](https://github.com/squidchemistry/interviewcoach/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Practice technical interviews with an AI interviewer. Pick a role and experience level, answer generated questions one at a time, and get a score (1–10), strengths, weaknesses and an improved answer for each. Every session is saved, so you can leave, come back, and review your history.

The backend is a FastAPI service in clean-architecture layers with PostgreSQL. The LLM is Groq, behind a deterministic mock provider, so the whole stack runs and is tested without an API key.

## Features

- **Persisted interview sessions**: questions, answers and evaluations are stored in Postgres; a refresh resumes where you left off.
- **History** with pagination, per-session progress and average score, and a results page with a server-computed summary.
- **Groq with model fallback**: an ordered model list with timeouts and retries; if a model is retired or failing, the next one is used.
- **Mock LLM provider**: deterministic and offline, used automatically when no `GROQ_API_KEY` is set.
- **Concurrency-safe answers**: a database unique constraint guarantees one answer per question, even for simultaneous requests (201 vs 409).
- **Per-IP rate limiting** on the endpoints that call the LLM, with `429` and `Retry-After`.
- **Liveness and readiness probes** (`/health`, `/ready`), JSON logs, one error shape for every failure.
- **Alembic migrations**, run automatically on container start, with a test that catches model/migration drift.
- **One-command local stack** (`docker compose up`), CI on every push, Render Blueprint and Vercel config for deployment.

## Architecture

```mermaid
flowchart LR
    subgraph Client
        SPA["React SPA<br/>(Vercel, or nginx locally)"]
    end
    subgraph API["FastAPI service (Render, Docker)"]
        Routes["API layer<br/>routes, validation,<br/>rate limit, errors"]
        UC["Application layer<br/>use cases + ports"]
        Dom["Domain layer<br/>InterviewSession rules"]
        Infra["Infrastructure<br/>SQLAlchemy repository,<br/>Groq / Mock adapters"]
    end
    DB[("PostgreSQL 16")]
    Groq["Groq API"]

    SPA -->|"JSON over HTTPS"| Routes
    Routes --> UC --> Dom
    Infra -. implements ports .-> UC
    Infra --> DB
    Infra --> Groq
```

Dependencies point inward: the domain and application layers import no FastAPI, SQLAlchemy or Groq code, and a test enforces it. More detail, including sequence and data-model diagrams, is in [docs/architecture.md](docs/architecture.md).

## Tech stack

| Area | Technology |
|---|---|
| API | Python 3.11, FastAPI, Pydantic v2, Uvicorn |
| Data | PostgreSQL 16, SQLAlchemy 2.0, Alembic, psycopg 3 |
| LLM | Groq SDK (model fallback list) and a deterministic mock |
| Frontend | React 19, React Router 7, Create React App |
| Tests | pytest (SQLite or Postgres), Jest + React Testing Library |
| Delivery | Docker multi-stage images, Docker Compose, GitHub Actions, gitleaks, Render, Vercel |

## Run locally

**With Docker (recommended).** Needs Docker only.

```bash
git clone https://github.com/squidchemistry/interviewcoach.git
cd interviewcoach
docker compose up --build
```

- App: <http://localhost:3000>
- API docs (Swagger UI): <http://localhost:8000/docs>
- Readiness: <http://localhost:8000/ready>

This starts Postgres, the API (migrations and 3 demo sessions are applied on start) and the frontend. Without a key the API uses the mock LLM. To use Groq, create a `.env` next to `docker-compose.yml` with `GROQ_API_KEY=...` and run the command again.

**Without Docker.** Needs Python 3.10+, Node 22, and a Postgres database (or set `DATABASE_URL=sqlite:///./local.db` for a quick try).

```bash
python -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt
cp .env.example .env                 # then edit DATABASE_URL / GROQ_API_KEY
alembic upgrade head
python -m app.infrastructure.seed    # optional demo data
uvicorn main:app --reload
```

```bash
cd frontend
cp .env.example .env.local           # REACT_APP_API_URL=http://localhost:8000
npm ci
npm start
```

## Configuration

Every setting is listed with a safe default in [.env.example](.env.example). Never commit a real `.env`. The ones that matter most:

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | local Postgres | SQLAlchemy URL. `postgres://` URLs (as Render provides) are accepted |
| `LLM_PROVIDER` | `auto` | `auto` = Groq if `GROQ_API_KEY` is set, else mock. `groq` or `mock` force one |
| `GROQ_API_KEY` | empty | Groq key (secret) |
| `GROQ_MODELS` | `llama-3.3-70b-versatile,openai/gpt-oss-20b,llama-3.1-8b-instant` | Models tried in order |
| `LLM_TIMEOUT_SECONDS`, `LLM_MAX_RETRIES` | `20`, `2` | Per-call timeout and retries before falling back |
| `CORS_ORIGINS` | `http://localhost:3000,...` | Comma-separated origins allowed to call the API |
| `RATE_LIMIT_PER_MINUTE` | `20` | LLM-backed requests per client IP per minute; `0` disables |
| `SEED_DEMO_DATA` | `false` | Seed demo sessions on start (only into an empty database) |
| `DB_POOL_SIZE`, `DB_MAX_OVERFLOW` | `5`, `5` | Connection pool per process |
| `ENABLE_DOCS` | `true` | Serve `/docs` and `/redoc` |

The frontend has one build-time variable, `REACT_APP_API_URL` (see [frontend/.env.example](frontend/.env.example)).

## Tests

```bash
ruff check .
pytest                                   # SQLite, no setup needed
TEST_DATABASE_URL=postgresql+psycopg://user:pass@localhost:5432/coach_test pytest   # against Postgres
cd frontend && CI=true npm test -- --watchAll=false && npm run build
```

The backend suite covers the domain rules, use cases with in-memory fakes, Groq fallback with a scripted client, the mock provider, settings parsing, the layer-boundary rule, migration drift, the concurrent duplicate-answer race, rate limiting, and the API on a migrated database. The frontend tests cover the API client, routes, the history page and the interview runner.

[CI](.github/workflows/ci.yml) runs all of this on every push and pull request (pytest against a Postgres service container), builds both Docker images, starts the compose stack for a smoke test, and scans the full git history with gitleaks.

## Deploy

The API and its database go to **Render**, the frontend to **Vercel**. Deploy the API first, because the frontend needs its URL.

**1. API + Postgres on Render (Blueprint)**

1. Render dashboard → **New** → **Blueprint** → connect this repository. Render reads [render.yaml](render.yaml), which creates a free Postgres database and a Docker web service with `DATABASE_URL` wired from the database and its health check on `/ready`.
2. `render.yaml` deploys the `main` branch. To deploy before merging, change `branch:` to `demo-ready`.
3. When prompted, set `GROQ_API_KEY` (or leave it empty to run on the mock) and `CORS_ORIGINS` (use `http://localhost:3000` for now).
4. When the deploy is live, open `https://<your-service>.onrender.com/ready`; it should return `"status":"ready"`.

**2. Frontend on Vercel**

1. Vercel → **Add New Project** → import this repository, and set **Root Directory** to `frontend`. [frontend/vercel.json](frontend/vercel.json) sets the build and the SPA rewrite.
2. Add the environment variable `REACT_APP_API_URL` = your Render URL (no trailing slash), then deploy. It is inlined at build time, so redeploy after changing it.

**3. Connect them**

In Render, set `CORS_ORIGINS` to your Vercel URL, for example `https://your-app.vercel.app` (exact origin, no trailing slash; separate several with commas). The service restarts with the new value.

Free-tier notes: Render's free web services sleep when idle, so the first request after a pause is slow, and free databases have a limited lifetime. Check Render's current free-tier terms.

## API

Interactive docs at `/docs`. Every error has the shape `{"error": {"code", "message", "details"}}`.

| Method | Path | Description | Responses |
|---|---|---|---|
| `GET` | `/health` | Liveness: the process is up | 200 |
| `GET` | `/ready` | Readiness: database reachable; reports the LLM provider | 200, 503 |
| `POST` | `/v1/sessions` | Create a session; generates 1–10 questions (`num_questions`, default 5) | 201, 422, 429 |
| `GET` | `/v1/sessions?limit=&offset=` | List sessions, newest first (`limit` 1–100, default 20) | 200, 422 |
| `GET` | `/v1/sessions/{id}` | Session with questions, answers and evaluations | 200, 404 |
| `POST` | `/v1/sessions/{id}/answers` | Answer one question; evaluated and stored | 201, 404, 409, 422, 429 |
| `GET` | `/v1/sessions/{id}/summary` | Average, best and worst score, strengths and weaknesses | 200, 404 |
| `POST` | `/v1/interview/generate-questions` | Stateless: generate questions, nothing stored | 200, 422, 429 |
| `POST` | `/v1/interview/evaluate` | Stateless: evaluate one answer, nothing stored | 200, 422, 429 |

Example:

```bash
curl -X POST localhost:8000/v1/sessions -H 'Content-Type: application/json' \
  -d '{"role": "Backend Engineer", "experience": "2-5 years", "num_questions": 3}'
```

## Project structure

```
app/
  api/             FastAPI routes, dependencies, error handlers, rate limiter
  application/     use cases, DTOs, ports (AIService, InterviewSessionRepository)
  domain/          InterviewSession aggregate and its rules (no framework imports)
  infrastructure/  Groq + mock adapters, SQLAlchemy models/repository, settings, logging, seed
  shared/          error types, Result
migrations/        Alembic migrations
tests/             unit/ (domain, use cases, adapters) and api/ (HTTP, DB, migrations)
frontend/          React app (own Dockerfile, nginx.conf, vercel.json)
scripts/start.sh   container entrypoint: migrate, seed, serve
docs/              architecture
```

## Limitations

- No authentication: anyone with the URL shares one session history. It's a public practice tool for now.
- Rate-limit counters are in process memory, so the limit is per API instance. A shared store (Redis) is the upgrade for several instances.
- LLM calls run inside the request; at higher load they would move to a job queue.

See [DEMO.md](DEMO.md) for a demo script and design Q&A.

## License

MIT, see [LICENSE](LICENSE). Built by Arsh Verma ([@squidchemistry](https://github.com/squidchemistry)).
