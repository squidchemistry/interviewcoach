"""
Liveness and readiness probes.

- /health: the process is up. No dependencies checked, so a slow database
  never makes the platform restart a healthy container.
- /ready: the app can serve traffic (database reachable). Returns 503 if not.
"""

import logging

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from app.infrastructure.persistence.database import ping

logger = logging.getLogger(__name__)

router = APIRouter()


@router.get("/health")
def health(request: Request) -> dict:
    return {"status": "ok", "version": request.app.version}


@router.get("/ping", include_in_schema=False)
def ping_legacy() -> dict:
    """Kept for older clients; same as /health."""
    return {"status": "alive"}


@router.get("/ready")
def ready(request: Request) -> JSONResponse:
    checks: dict[str, str] = {}
    try:
        ping(request.app.state.engine)
        checks["database"] = "ok"
    except Exception as exc:  # any driver error means "not ready"
        logger.warning("readiness_database_failed", extra={"error": type(exc).__name__})
        checks["database"] = "unavailable"
    checks["llm_provider"] = request.app.state.ai_service.name
    is_ready = checks["database"] == "ok"
    return JSONResponse(
        status_code=200 if is_ready else 503,
        content={"status": "ready" if is_ready else "not_ready", "checks": checks},
    )
