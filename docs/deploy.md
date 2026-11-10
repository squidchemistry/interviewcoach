# Deploying AI Interview Coach

This guide takes the Docker Compose stack from a laptop to a single Linux VM with HTTPS. It is the cheapest
production-like setup: one VM with 2 vCPU and 4 GB RAM runs the API, the web server, PostgreSQL and Redis. Judge0
needs a separate VM; see §5.

## 1. What runs where

| Service | Image | Exposed | Notes |
|---|---|---|---|
| `web` | `frontend/Dockerfile` (nginx) | 80 → your TLS proxy | Serves the SPA. Proxies `/api/` to `api`, with SSE buffering off. |
| `api` | `backend/Dockerfile` (Temurin 25 JRE, non-root) | internal only in production | Port 8081 (actuator) is never published. |
| `postgres` | `pgvector/pgvector:pg16` | loopback only | Volume `pgdata`. |
| `redis` | `redis:7-alpine` | loopback only | Event streams for SSE, rate limits and AI cache. |
| `judge0-*` | Judge0 CE 1.13 | loopback only | Optional; needs cgroup v1 (§5). |

## 2. Prepare the VM

```bash
# Ubuntu 24.04
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 caddy
sudo usermod -aG docker $USER   # log out and back in
git clone <your repo> ai-interview-coach && cd ai-interview-coach
cp .env.example .env
```

## 3. Production `.env`

Change **every** value below. Never commit `.env`.

| Variable | Production value |
|---|---|
| `POSTGRES_PASSWORD`, `REDIS_PASSWORD` | long random strings (`openssl rand -base64 32`) |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `COOKIE_SECURE` | `true` |
| `WEB_BASE_URL` | `https://coach.example.com` |
| `CORS_ORIGINS` | `https://coach.example.com` |
| `SEED_DEMO_DATA` | `false`, or `true` with strong `DEMO_USER_PASSWORD` / `ADMIN_PASSWORD` for a public demo |
| `AI_PROVIDER` | `anthropic` (or `openai`), plus `AI_API_KEY` |
| `AI_EMBEDDING_PROVIDER` | `openai` with `AI_EMBEDDING_API_KEY`, or keep `mock` (similar-questions quality drops) |
| `CODE_EXECUTOR` | `judge0` with `JUDGE0_URL` and `JUDGE0_AUTH_TOKEN` (§5) |
| `PAYMENTS_PROVIDER` | `razorpay` with the four `RAZORPAY_*` values (§6) |
| `GOOGLE_*` | optional; the redirect URI becomes `https://coach.example.com/api/v1/auth/google/callback` |

Run the API with the `prod` Spring profile (`SPRING_PROFILES_ACTIVE=prod` in the `api` service environment). With
that profile the API **refuses to start** if `CODE_EXECUTOR=mock` or `PAYMENTS_PROVIDER=mock`, so a demo
configuration cannot reach production by accident. For a public demo that deliberately runs the mocks, leave the
profile unset; the UI labels every mock result.

## 4. Start and put TLS in front

```bash
docker compose up --build -d --wait
docker compose ps        # every service "healthy"
```

`/etc/caddy/Caddyfile` (Caddy obtains and renews Let's Encrypt certificates automatically):

```
coach.example.com {
    reverse_proxy 127.0.0.1:8088 {
        flush_interval -1      # stream SSE immediately
    }
}
```

`sudo systemctl reload caddy`. In `docker-compose.yml`, bind `web` to loopback (`"127.0.0.1:${WEB_PORT}:80"`) and
remove the published `api` port, so only Caddy is reachable from the internet.

Check:

```bash
curl -fsS https://coach.example.com/api/v1/health      # {"status":"UP",...}
```

## 5. Judge0 (real code execution)

Judge0's sandbox (`isolate`) needs **cgroup v1**. Docker Desktop and recent Ubuntu use cgroup v2, where Judge0
fails every submission (risk R6). Pick one option:

- **Dedicated VM with cgroup v1**: Ubuntu 20.04, or boot with `systemd.unified_cgroup_hierarchy=0`. Run only the
  `judge0-*` services there (`docker compose --profile judge0 up -d`). Point `JUDGE0_URL` at it over a private
  network and set `JUDGE0_AUTH_TOKEN`. Never expose port 2358 publicly: the container is privileged.
- **RapidAPI-hosted Judge0**: `JUDGE0_URL=https://judge0-ce.p.rapidapi.com`, `JUDGE0_AUTH_HEADER=X-RapidAPI-Key`,
  `JUDGE0_AUTH_TOKEN=<key>`, `JUDGE0_RAPIDAPI_HOST=judge0-ce.p.rapidapi.com`. Quotas apply.

The API validates the setup lazily: `POST /sessions/{id}/code/run` returns `503 CODE_EXECUTOR_UNAVAILABLE` if
Judge0 can't be reached.

## 6. Razorpay

1. Razorpay Dashboard → Subscriptions → create a monthly plan at ₹499. Copy the plan id into `RAZORPAY_PLAN_ID`.
2. Settings → API keys: `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`.
3. Settings → Webhooks: URL `https://coach.example.com/api/v1/webhooks/razorpay`. Set a secret
   (`RAZORPAY_WEBHOOK_SECRET`) and enable the `subscription.*` events.
4. Test with Razorpay test-mode keys first. The user's plan changes **only** when a signed webhook arrives. Webhook
   redeliveries are idempotent (`payment_events.event_id` is unique).

## 7. Operations

- **Backups**: `docker compose exec postgres pg_dump -U aicoach aicoach | gzip > backup-$(date +%F).sql.gz` from a
  daily cron job; copy the file off the VM. Redis holds only reconstructible data (event streams, caches, limits).
- **Upgrades**: `git pull && docker compose up --build -d --wait`. Flyway applies migrations on start, and
  `ddl-auto=validate` stops the API if the schema and entities disagree.
- **Logs**: the API logs JSON (logstash format) to stdout: `docker compose logs -f api`. Every line carries the
  request id that is echoed in `X-Request-Id`.
- **Metrics**: Prometheus format on the internal management port (`api:8081/actuator/prometheus`). AI token usage
  per day, purpose and model is at **Admin → AI usage**.
- **Scaling out**: the API is stateless (JWT, events in Redis Streams, optimistic locking on sessions), so several
  `api` replicas can run behind nginx. Background work (generation, evaluation, sweeper) runs in-process on virtual
  threads; with several replicas the sweeper may re-run a stalled evaluation twice, which only recomputes the same
  result.
