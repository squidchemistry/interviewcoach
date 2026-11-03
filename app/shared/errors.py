from http import HTTPStatus
from typing import Any


class AppError(Exception):
    """
    Base class for all application-level errors.

    Subclasses can override `status_code` and `code` to control how they
    are rendered in HTTP responses.
    """

    status_code: int = HTTPStatus.INTERNAL_SERVER_ERROR
    code: str = "internal_error"

    def __init__(self, message: str, *, details: dict[str, Any] | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details or {}

    def to_dict(self) -> dict[str, Any]:
        return {
            "error": {
                "code": self.code,
                "message": self.message,
                "details": self.details,
            }
        }


class ValidationError(AppError):
    status_code = HTTPStatus.BAD_REQUEST
    code = "validation_error"


class DomainError(AppError):
    status_code = HTTPStatus.UNPROCESSABLE_ENTITY
    code = "domain_error"


class NotFoundError(AppError):
    status_code = HTTPStatus.NOT_FOUND
    code = "not_found"


class ConflictError(AppError):
    status_code = HTTPStatus.CONFLICT
    code = "conflict"


class InfrastructureError(AppError):
    status_code = HTTPStatus.SERVICE_UNAVAILABLE
    code = "infrastructure_error"



class RateLimitedError(AppError):
    status_code = HTTPStatus.TOO_MANY_REQUESTS
    code = "rate_limited"

    def __init__(self, message: str, *, retry_after_seconds: int, details: dict[str, Any] | None = None) -> None:
        super().__init__(message, details=details)
        self.retry_after_seconds = retry_after_seconds
