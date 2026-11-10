package com.aicoach.ai.service.decorator;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import com.aicoach.common.error.ErrorCode;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Decorator: fails fast while the provider is unhealthy instead of making every user wait for timeouts.
 * Sits outside {@link RetryingLlmProvider}, so one logical call (with its retries) is one breaker sample.
 */
public class CircuitBreakerLlmProvider implements LlmProvider {

    private final LlmProvider inner;
    private final CircuitBreaker breaker;

    public CircuitBreakerLlmProvider(LlmProvider inner, CircuitBreaker breaker) {
        this.inner = inner;
        this.breaker = breaker;
    }

    public CircuitBreaker.State state() {
        return breaker.state();
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        return guarded(() -> inner.complete(request));
    }

    @Override
    public LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
        return guarded(() -> inner.stream(request, onDelta));
    }

    private LlmResult guarded(Supplier<LlmResult> call) {
        if (!breaker.tryAcquire()) {
            throw new AiException(ErrorCode.AI_UNAVAILABLE,
                    "The AI interviewer is temporarily unavailable. Your progress is saved; please retry shortly.",
                    "CIRCUIT_OPEN");
        }
        try {
            LlmResult result = call.get();
            breaker.onSuccess();
            return result;
        } catch (AiException e) {
            if (e.errorCode() == ErrorCode.AI_UNAVAILABLE) {
                breaker.onFailure();
            } else {
                breaker.onSuccess();
            }
            throw e;
        } catch (RuntimeException e) {
            breaker.onFailure();
            throw e;
        }
    }
}
