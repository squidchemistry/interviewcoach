from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, Optional, TypeVar

from .errors import AppError

T = TypeVar("T")


@dataclass
class Result(Generic[T]):
    """
    A simple Result type that wraps either a value or an error.

    This is useful in the application layer to return domain/app errors
    without throwing exceptions.
    """

    value: Optional[T] = None
    error: Optional[AppError] = None

    @property
    def is_ok(self) -> bool:
        return self.error is None

    @property
    def is_err(self) -> bool:
        return self.error is not None

    @staticmethod
    def ok(value: T) -> Result[T]:
        return Result(value=value)

    @staticmethod
    def err(error: AppError) -> Result[T]:
        return Result(error=error)

