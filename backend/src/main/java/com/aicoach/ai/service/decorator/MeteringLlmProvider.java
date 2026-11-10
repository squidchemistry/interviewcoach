package com.aicoach.ai.service.decorator;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import com.aicoach.ai.repository.AiCallLogRepository;
import com.aicoach.ai.repository.AiCallLogRepository.AiCallLog;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.web.RequestIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Outermost decorator: enforces per-session and per-user daily token budgets, then records every call (including
 * cache hits, breaker rejections and failures) to {@code ai_call_logs} and Micrometer.
 */
public class MeteringLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(MeteringLlmProvider.class);

    private final LlmProvider inner;
    private final AiCallLogRepository logs;
    private final MeterRegistry meters;
    private final Clock clock;
    private final int sessionBudget;
    private final int dailyBudget;

    public MeteringLlmProvider(LlmProvider inner, AiCallLogRepository logs, MeterRegistry meters, Clock clock,
            int sessionBudget, int dailyBudget) {
        this.inner = inner;
        this.logs = logs;
        this.meters = meters;
        this.clock = clock;
        this.sessionBudget = sessionBudget;
        this.dailyBudget = dailyBudget;
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        return metered(request, () -> inner.complete(request));
    }

    @Override
    public LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
        return metered(request, () -> inner.stream(request, onDelta));
    }

    private LlmResult metered(LlmRequest request, Supplier<LlmResult> call) {
        long start = System.nanoTime();
        try {
            checkBudget(request.context());
            LlmResult result = call.get();
            record(request, result, start, "OK", null);
            return result;
        } catch (AiException e) {
            record(request, null, start, e.logStatus(), e.errorCode().name());
            throw e;
        } catch (RuntimeException e) {
            record(request, null, start, "ERROR", e.getClass().getSimpleName());
            throw e;
        }
    }

    private void checkBudget(CallContext context) {
        if (context.sessionId() != null && logs.tokensForSession(context.sessionId()) >= sessionBudget) {
            throw new AiException(ErrorCode.TOKEN_BUDGET_EXCEEDED, "This interview has used its AI token budget",
                    "BUDGET_EXCEEDED");
        }
        if (context.userId() != null
                && logs.tokensForUserSince(context.userId(), clock.instant().truncatedTo(ChronoUnit.DAYS)) >= dailyBudget) {
            throw new AiException(ErrorCode.TOKEN_BUDGET_EXCEEDED, "You have reached today's AI usage limit",
                    "BUDGET_EXCEEDED");
        }
    }

    private void record(LlmRequest request, LlmResult result, long startNanos, String status, String errorCode) {
        long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        boolean cached = result != null && result.cached();
        try {
            CallContext ctx = request.context();
            logs.insert(new AiCallLog(MDC.get(RequestIdFilter.MDC_KEY), ctx.userId(), ctx.sessionId(),
                    ctx.purpose().name(), inner.name(), result != null ? result.model() : request.model(),
                    request.promptId(), request.promptVersion(),
                    cached || result == null ? 0 : result.inputTokens(),
                    cached || result == null ? 0 : result.outputTokens(),
                    (int) latencyMs, cached, status, errorCode));
        } catch (RuntimeException e) {
            log.warn("Failed to record AI call log: {}", e.toString());
        }
        Timer.builder("ai.calls").tag("provider", inner.name()).tag("purpose", request.context().purpose().name())
                .tag("status", status).tag("cache", Boolean.toString(cached))
                .register(meters).record(Duration.ofMillis(latencyMs));
        if (result != null && !cached) {
            meters.counter("ai.tokens", "provider", inner.name(), "direction", "input").increment(result.inputTokens());
            meters.counter("ai.tokens", "provider", inner.name(), "direction", "output").increment(result.outputTokens());
        }
    }
}
