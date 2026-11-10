#!/usr/bin/env bash
# Runs the full Phase verification: backend verify, frontend checks, docker compose up, smoke tests.
# Usage (from the repo root):  ./infra/scripts/verify.sh [--skip-docker]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

step() { printf '\n=== %s ===\n' "$1"; }

major="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')" || true
if [[ -z "${major:-}" || "$major" -lt 25 ]]; then
  echo "JDK 25+ required (found: ${major:-none}). Install Temurin 25 and set JAVA_HOME." >&2; exit 1
fi

step "Backend: mvnw verify (unit + integration + ArchUnit + coverage)"
(cd backend && ./mvnw -B -ntp verify)

step "Frontend: install, lint, typecheck, test, build"
(cd frontend && npm ci --no-audit --no-fund && npm run lint && npm run typecheck && npm test && npm run build)

if [[ "${1:-}" != "--skip-docker" ]]; then
  [[ -f .env ]] || { cp .env.example .env; echo "Created .env from .env.example"; }
  WEB_PORT="$(grep -E '^WEB_PORT=' .env | cut -d= -f2)"; WEB_PORT="${WEB_PORT:-8088}"
  API_PORT="$(grep -E '^API_PORT=' .env | cut -d= -f2)"; API_PORT="${API_PORT:-8080}"

  step "Docker: compose up --build --wait"
  docker compose up --build -d --wait --wait-timeout 420

  step "Smoke tests"
  curl -fsS "http://127.0.0.1:${API_PORT}/api/v1/health" | tee /dev/stderr | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${WEB_PORT}/api/v1/health" | grep -q '"status":"UP"'
  curl -fsS "http://127.0.0.1:${WEB_PORT}/" | grep -q 'id="root"'
  echo "Web OK at http://localhost:${WEB_PORT}"
  if grep -qE '^SEED_DEMO_DATA=true' .env; then
    DEMO_PASSWORD="$(grep -E '^DEMO_USER_PASSWORD=' .env | cut -d= -f2)"
    curl -fsS -X POST -H 'Content-Type: application/json' \
      -d "{\"email\":\"demo@aicoach.dev\",\"password\":\"${DEMO_PASSWORD}\"}" \
      "http://127.0.0.1:${WEB_PORT}/api/v1/auth/login" | grep -q '"accessToken"'
    echo "Demo login OK"
  fi
fi

printf '\nAll checks passed.\n'
