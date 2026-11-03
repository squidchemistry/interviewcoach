import pytest

from app.api.rate_limit import SlidingWindowRateLimiter
from app.shared.errors import RateLimitedError


class FakeClock:
    def __init__(self) -> None:
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now


def test_allows_up_to_limit_then_blocks_until_window_slides():
    clock = FakeClock()
    limiter = SlidingWindowRateLimiter(limit=2, window_seconds=60, clock=clock)
    limiter.check("1.2.3.4")
    clock.now += 10
    limiter.check("1.2.3.4")

    with pytest.raises(RateLimitedError) as excinfo:
        limiter.check("1.2.3.4")
    assert excinfo.value.retry_after_seconds == 50  # oldest hit expires 60s after it happened

    limiter.check("5.6.7.8")  # other clients are independent

    clock.now += 50
    limiter.check("1.2.3.4")  # first hit has left the window


def test_zero_limit_disables():
    limiter = SlidingWindowRateLimiter(limit=0)
    for _ in range(100):
        limiter.check("x")


def test_idle_clients_are_forgotten():
    clock = FakeClock()
    limiter = SlidingWindowRateLimiter(limit=5, window_seconds=60, clock=clock)
    limiter.MAX_TRACKED_CLIENTS = 3
    for ip in ("a", "b", "c"):
        limiter.check(ip)
    clock.now += 61
    limiter.check("d")
    assert set(limiter._hits) == {"d"}
