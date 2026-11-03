"""
Per-client rate limiting for the endpoints that call the LLM.

Sliding window over the last 60 seconds, keyed by client IP (uvicorn runs
with --proxy-headers, so behind Render's proxy this is the real client IP).

Trade-off: counters live in process memory, so with N API instances a client
can make up to N x the limit. That is acceptable for a free-tier single
instance; the fix for horizontal scaling is the same interface backed by a
shared store (Redis INCR + EXPIRE).
"""

import math
import threading
import time
from collections import defaultdict, deque
from collections.abc import Callable

from fastapi import Request

from app.shared.errors import RateLimitedError


class SlidingWindowRateLimiter:
    def __init__(self, limit: int, window_seconds: float = 60.0, clock: Callable[[], float] = time.monotonic):
        self.limit = limit
        self.window = window_seconds
        self._clock = clock
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def check(self, key: str) -> None:
        """Record a hit for `key`, or raise RateLimitedError if over the limit."""
        if self.limit <= 0:
            return
        now = self._clock()
        with self._lock:
            hits = self._hits[key]
            while hits and hits[0] <= now - self.window:
                hits.popleft()
            if len(hits) >= self.limit:
                retry_after = max(1, math.ceil(hits[0] + self.window - now))
                raise RateLimitedError(
                    "Too many requests, please slow down",
                    retry_after_seconds=retry_after,
                    details={"limit": self.limit, "window_seconds": int(self.window)},
                )
            hits.append(now)
            if len(self._hits) > self.MAX_TRACKED_CLIENTS:
                self._forget_idle_clients(now)

    MAX_TRACKED_CLIENTS = 10_000

    def _forget_idle_clients(self, now: float) -> None:
        # Keeps memory bounded when many one-off clients pass through.
        idle = [k for k, hits in self._hits.items() if not hits or hits[-1] <= now - self.window]
        for key in idle:
            del self._hits[key]


def limit_llm_calls(request: Request) -> None:
    """FastAPI dependency for routes that spend an LLM call."""
    client = request.client.host if request.client else "unknown"
    request.app.state.rate_limiter.check(client)
