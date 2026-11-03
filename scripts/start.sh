#!/bin/sh
# Container entrypoint: migrate, optionally seed demo data, then serve.
set -e

alembic upgrade head

case "${SEED_DEMO_DATA:-false}" in
  true|1|yes) python -m app.infrastructure.seed ;;
esac

exec uvicorn main:app \
  --host 0.0.0.0 \
  --port "${PORT:-8000}" \
  --proxy-headers \
  --forwarded-allow-ips "*" \
  --no-access-log
