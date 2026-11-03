# Demo script: AI Interview Coach

A 3–5 minute walkthrough, then 10 interview questions about the design with short answers.

Commands are for bash (Git Bash on Windows works). Replace `<api>` and `<web>` with your Render and Vercel URLs.

## Before the demo

- Local: Docker Desktop running, ports 3000 and 8000 free. No API key needed.
- Deployed: Render's free tier stops idle services, so open `https://<api>/ready` a minute before you start and wait for `"status":"ready"`.
- Tabs to have open: the app, `/docs` (Swagger UI), this file, and the GitHub Actions page.

## Part 1: local, mock mode (about 2.5 minutes)

**1. One command starts everything (20 s).**

```bash
docker compose down -v   # optional: start from an empty database so History shows just the 3 seeded sessions
docker compose up --build -d --wait
```

Say: "This starts Postgres 16, the FastAPI API and the React build behind nginx. The API container runs the Alembic migrations, seeds three demo sessions, then starts uvicorn. There's no Groq key in this shell, so the API uses a deterministic mock LLM."

**2. Health vs readiness (20 s).**

```bash
curl -s localhost:8000/health
curl -s localhost:8000/ready
```

Say: "`/health` only says the process is up. `/ready` checks the database and tells you which LLM provider is active: `mock` here."

**3. The product (60 s).** Open <http://localhost:3000>.

1. **History**: three seeded sessions (completed, partly answered, not started) with status, progress and average score. Pagination is in the URL.
2. **Start interview**: role `Backend Engineer`, experience `2-5 years`. Five questions come back, the first one about pagination.
3. Answer one question with two sentences, then one with a longer answer that has an example and a number. Scores and feedback differ: the mock rewards depth, reasoning words and concrete numbers.
4. **Refresh the page** mid-interview. It resumes at the next unanswered question, because every answer is stored server-side.
5. Finish (or open a completed session from History) to show the summary: average, best and worst score, strengths and weaknesses.

**4. Concurrency: the unique constraint (30 s).** Two simultaneous submissions of the same answer:

```bash
S=$(curl -s -X POST localhost:8000/v1/sessions -H 'Content-Type: application/json' \
  -d '{"role":"Backend Engineer","experience":"2-5 years","num_questions":2}')
SID=$(echo "$S" | python -c "import sys,json; print(json.load(sys.stdin)['id'])")
QID=$(echo "$S" | python -c "import sys,json; print(json.load(sys.stdin)['questions'][0]['id'])")
for i in 1 2; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "localhost:8000/v1/sessions/$SID/answers" \
    -H 'Content-Type: application/json' -d "{\"question_id\":\"$QID\",\"answer\":\"same answer\"}" &
done; wait
```

Expected: one `201` and one `409`. Say: "Both requests pass the in-memory check. The database's UNIQUE constraint on `answers.question_id` decides the winner, and the loser maps to a 409, not a 500."

**5. Rate limiting (20 s).**

```bash
for i in $(seq 1 22); do
  curl -s -o /dev/null -w "%{http_code} " -X POST localhost:8000/v1/interview/evaluate \
    -H 'Content-Type: application/json' -d '{"question":"q","answer":"a"}'
done; echo
curl -si -X POST localhost:8000/v1/interview/evaluate -H 'Content-Type: application/json' \
  -d '{"question":"q","answer":"a"}' | grep -iE '^HTTP|retry-after|"code"'
```

Expected: `200`s, then `429` once the per-minute limit (20 by default) is used up, with a `Retry-After` header and the usual error shape. Do this last: the limit then also blocks new sessions from your IP for up to a minute.

## Part 2: deployed (about 1.5 minutes)

1. `https://<api>/ready` returns `"database":"ok"` and `"llm_provider":"groq"` once `GROQ_API_KEY` is set on Render.
2. Open `https://<web>`, start an interview. The questions now come from Groq. Say: "Same code, same image. Only the environment changed: the provider is chosen at startup from `LLM_PROVIDER` and `GROQ_API_KEY`."
3. Open `https://<api>/docs` to show the API contract (Swagger UI).
4. Show `render.yaml`: one Blueprint creates the Docker web service and the managed Postgres, wires `DATABASE_URL` from the database, and health-checks `/ready`, so a deploy only goes live once it can reach the database.
5. Show the GitHub Actions run: ruff, pytest against a Postgres service container, frontend tests and build, both Docker images built plus a compose smoke test, and a gitleaks secret scan.

Clean up the local stack with `docker compose down -v`.

## 10 interview questions

**1. Why clean architecture for an app this size?**
The value is in the boundaries, not the folders. The domain (`InterviewSession` and its rules) and the use cases import no FastAPI, SQLAlchemy or Groq; `tests/unit/test_architecture.py` fails the build if they do. So the business rules are tested with in-memory fakes in milliseconds, and swapping Groq for the mock, or SQLite for Postgres, touches only infrastructure. The cost is some mapping code between ORM rows and domain objects, which I accepted.

**2. What does the repository pattern give you here?**
The application layer depends on an `InterviewSessionRepository` port, not on SQLAlchemy. Use-case tests run against an in-memory fake; API tests run against a real migrated database (SQLite locally, Postgres in CI) through the SQLAlchemy adapter. The repository also owns transactions and the translation of an integrity error into a domain `ConflictError`.

**3. How does the model fallback work, and why have one?**
`GROQ_MODELS` is an ordered list. For each request the adapter tries the first model with a timeout and SDK retries with backoff; on a retired model, exhausted retries, an API error or JSON it can't parse, it moves to the next model. An authentication error stops at once, since another model won't fix a bad key. Hosted model IDs get deprecated, so the list is configuration, not code. The behaviour is tested with a scripted fake client.

**4. How do you stop two concurrent answers to the same question?**
The domain checks "already answered", but two requests can both pass that check before either writes. The real guarantee is a UNIQUE constraint on `answers.question_id`: the second insert fails, the repository turns that into a `ConflictError`, and the API returns 409. A test opens two database sessions and races them. I chose a constraint over `SELECT … FOR UPDATE` because it works identically on SQLite and Postgres and holds no locks.

**5. How is pagination done, and what would you change at scale?**
Offset pagination: `limit` (1–100, validated) and `offset`, newest first, with `total` in the response, backed by an index on `created_at`. It's simple and lets the UI jump to page N. For deep pages on large tables I'd switch to keyset pagination on `(created_at, id)`, which stays fast regardless of depth but gives up random page access.

**6. How does the rate limiting work, and what's its weakness?**
A sliding 60-second window per client IP, applied as a FastAPI dependency to the four endpoints that call the LLM, because those cost money and time. Over the limit you get a 429 in the standard error shape with `Retry-After`. Uvicorn runs with `--proxy-headers` so the IP is the real client behind Render's proxy. The weakness: counters live in process memory, so N instances allow N times the limit. The fix is the same interface backed by Redis (`INCR` + `EXPIRE`).

**7. Why a mock LLM provider?**
So the whole stack runs and tests pass with no API key, offline and for free, including in CI and for anyone cloning the repo. It is deterministic (same input, same output), so tests can assert on it, and it implements the same `AIService` port as Groq. `LLM_PROVIDER=auto` picks Groq only when a key is present; the active provider is visible in `/ready`.

**8. How are database migrations handled?**
Alembic, with the schema in versioned migration files rather than `create_all()`. The container entrypoint runs `alembic upgrade head` before starting the server, so every deploy migrates first. A drift test applies the migrations and compares the result to the ORM models, so a model change without a migration fails CI. With more than one instance I'd move migrations to a separate pre-deploy step so instances don't race.

**9. What's the difference between `/health` and `/ready`?**
`/health` is liveness: the process answers, nothing else is checked. The Docker `HEALTHCHECK` uses it, so a slow database never gets a working container restarted. `/ready` is readiness: it runs `SELECT 1` and returns 503 if the database is unreachable. Render's health check uses `/ready`, so traffic only goes to an instance that can serve it.

**10. How would this scale?**
The API is stateless (all session state is in Postgres), so it scales horizontally behind a load balancer. What I'd change, in order: move rate-limit counters to Redis; run migrations as a one-off release step; size the connection pool per instance (`DB_POOL_SIZE`, `DB_MAX_OVERFLOW`) against Postgres' connection limit, or add PgBouncer; move LLM calls to a background job queue so slow model responses don't tie up web workers; switch to keyset pagination; and add authentication so sessions belong to users. The frontend is static files on a CDN and needs nothing.
