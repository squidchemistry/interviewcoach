"""
API layer for the AI Interview Coach backend.

This package contains:
- FastAPI routers grouped by domain.
- Error handlers that map domain/app errors to HTTP responses.
"""

from .error_handlers import register_error_handlers  # noqa: F401
from .routes import api_router  # noqa: F401

