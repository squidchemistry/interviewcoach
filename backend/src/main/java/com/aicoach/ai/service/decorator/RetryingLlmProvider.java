package com.aicoach.ai.service.decorator;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Decorator: retries transient failures (429, 5xx, timeouts, I/O) with exponential backoff and full jitter.
 * Streams are retried only if no token has been emitted yet, so the client never sees duplicated text.
 */
public class RetryingLlmProvider implements LlmProvider {

    private final LlmProvider inner;
    private final int maxRetries;
    private final Duration baseBackoff;

    public RetryingLlmProvider(LlmProvider inner, int maxRetries, Duration baseBackoff) {
        this.inner = inner;
        this.maxRetries = maxRetries;
        this.baseBackoff = baseBackoff;
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        return withRetry(() -> inner.complete(request), () -> true);
    }

    @Override
    public LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
        AtomicBoolean emitted = new AtomicBoolean(false);
        return withRetry(() -> inner.stream(request, delta -> {
            emitted.set(true);
            onDelta.accept(delta);
        }), () -> !emitted.get());
    }

    private LlmResult withRetry(Supplier<LlmResult> call, Supplier<Boolean> retryAllowed) {
        int attempt = 0;
        while (true) {
            try {
                return call.get();
            } catch (AiException.Transient e) {
                if (attempt >= maxRetries || !retryAllowed.get()) {
                    throw e;
                }
                sleep(backoff(attempt++));
            }
        }
    }

    /** Full jitter: uniform in [0, base * 2^attempt], capped at 8 s. */
    Duration backoff(int attempt) {
        long cap = Math.min(8_000, baseBackoff.toMillis() * (1L << Math.min(attempt, 10)));
        return Duration.ofMillis(cap == 0 ? 0 : ThreadLocalRandom.current().nextLong(cap + 1));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException.Transient("Interrupted during retry backoff", e);
        }
    }
}
