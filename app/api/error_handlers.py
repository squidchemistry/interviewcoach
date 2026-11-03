"""
Map every error to one JSON shape:

    {"error": {"code": "...", "message": "...", "details": {...}}}
"""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.shared.errors import AppError, RateLimitedError

logger = logging.getLogger(__name__)

_HTTP_CODES = {
    400: "bad_request",
    401: "unauthorized",
    403: "forbidden",
    404: "not_found",
    405: "method_not_allowed",
    409: "conflict",
    429: "rate_limited",
}


def _error(status_code: int, code: str, message: str, details: dict | None = None) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"error": {"code": code, "message": message, "details": details or {}}},
    )


def register_error_handlers(app: FastAPI) -> None:
    """
    Register global exception handlers for application-specific errors.
    """

    @app.exception_handler(AppError)
    async def app_error_handler(request: Request, exc: AppError) -> JSONResponse:
        if exc.status_code >= 500:
            logger.warning("app_error", extra={"code": exc.code, "path": request.url.path})
        headers = None
        if isinstance(exc, RateLimitedError):
            headers = {"Retry-After": str(exc.retry_after_seconds)}
        return JSONResponse(status_code=exc.status_code, content=exc.to_dict(), headers=headers)

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        errors = [
            {"field": ".".join(str(p) for p in err.get("loc", [])), "message": err.get("msg", "")}
            for err in exc.errors()
        ]
        return _error(422, "validation_error", "Request validation failed", {"errors": errors})

    @app.exception_handler(StarletteHTTPException)
    async def http_error_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        code = _HTTP_CODES.get(exc.status_code, "http_error")
        return _error(exc.status_code, code, str(exc.detail))

    @app.exception_handler(Exception)
    async def unhandled_error_handler(request: Request, exc: Exception) -> JSONResponse:
        # Never leak internals to clients; the traceback goes to the logs.
        logger.exception("unhandled_error", extra={"path": request.url.path})
        return _error(500, "internal_error", "Internal server error")
