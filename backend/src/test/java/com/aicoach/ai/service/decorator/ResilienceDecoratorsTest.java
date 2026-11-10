package com.aicoach.ai.service.decorator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.AiTypes.ChatMessage;
import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import com.aicoach.ai.AiTypes.ModelTier;
import com.aicoach.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ResilienceDecoratorsTest {

    static final LlmRequest REQUEST = new LlmRequest("hint", "1", ModelTier.FAST, "m", "s",
            List.of(ChatMessage.user("u")), 0, 10, false, Map.of(), CallContext.system(AiPurpose.HINT));

    /** Fails {@code failures} times with the given exception, then succeeds. */
    static final class Flaky implements LlmProvider {
        final AtomicInteger calls = new AtomicInteger();
        final int failures;
        final RuntimeException error;

        Flaky(int failures, RuntimeException error) {
            this.failures = failures;
            this.error = error;
        }

        @Override
        public String name() {
            return "flaky";
        }

        @Override
        public LlmResult complete(LlmRequest request) {
            if (calls.incrementAndGet() <= failures) {
                throw error;
            }
            return new LlmResult("ok", 1, 1, "m", false);
        }

        @Override
        public LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
            return complete(request);
        }
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-01T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void retriesTransientFailuresOnly() {
        Flaky transientTwice = new Flaky(2, new AiException.Transient("503", null));
        assertThat(new RetryingLlmProvider(transientTwice, 2, Duration.ofMillis(1)).complete(REQUEST).text()).isEqualTo("ok");
        assertThat(transientTwice.calls).hasValue(3);

        Flaky alwaysTransient = new Flaky(10, new AiException.Transient("503", null));
        assertThatThrownBy(() -> new RetryingLlmProvider(alwaysTransient, 2, Duration.ofMillis(1)).complete(REQUEST))
                .isInstanceOf(AiException.Transient.class);
        assertThat(alwaysTransient.calls).hasValue(3);

        Flaky permanent = new Flaky(10, new AiException(ErrorCode.AI_UNAVAILABLE, "401", "ERROR"));
        assertThatThrownBy(() -> new RetryingLlmProvider(permanent, 2, Duration.ofMillis(1)).complete(REQUEST))
                .isInstanceOf(AiException.class);
        assertThat(permanent.calls).hasValue(1);
        assertThat(new RetryingLlmProvider(permanent, 2, Duration.ofMillis(1)).name()).isEqualTo("flaky");
    }

    @Test
    void circuitOpensAfterFailuresAndRecoversThroughHalfOpen() {
        MutableClock clock = new MutableClock();
        CircuitBreaker breaker = new CircuitBreaker(10, 4, 0.5, Duration.ofSeconds(30), clock);
        Flaky down = new Flaky(1_000, new AiException.Transient("503", null));
        CircuitBreakerLlmProvider provider = new CircuitBreakerLlmProvider(down, breaker);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> provider.complete(REQUEST)).isInstanceOf(AiException.Transient.class);
        }
        assertThat(provider.state()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsWhenOpened = down.calls.get();
        assertThatThrownBy(() -> provider.complete(REQUEST)).isInstanceOf(AiException.class);
        assertThat(down.calls.get()).as("open circuit fails fast").isEqualTo(callsWhenOpened);

        clock.now = clock.now.plusSeconds(31);
        assertThat(breaker.tryAcquire()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        breaker.onSuccess();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);

        CircuitBreakerLlmProvider healthy = new CircuitBreakerLlmProvider(new Flaky(0, null), breaker);
        assertThat(healthy.complete(REQUEST).text()).isEqualTo("ok");
        assertThat(healthy.stream(REQUEST, d -> { }).text()).isEqualTo("ok");
    }

    @Test
    void halfOpenFailureReopensTheCircuit() {
        MutableClock clock = new MutableClock();
        CircuitBreaker breaker = new CircuitBreaker(4, 2, 0.5, Duration.ofSeconds(10), clock);
        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
        clock.now = clock.now.plusSeconds(11);
        assertThat(breaker.tryAcquire()).isTrue();
        breaker.onFailure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
