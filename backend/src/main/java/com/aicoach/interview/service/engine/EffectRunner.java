package com.aicoach.interview.service.engine;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.schema.TurnAssessment;
import com.aicoach.ai.service.AiGateway;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.interview.domain.InterviewEnums.TurnIntent;
import com.aicoach.interview.domain.InterviewEnums.TurnKind;
import com.aicoach.interview.domain.InterviewEnums.TurnRole;
import com.aicoach.interview.domain.Turn;
import com.aicoach.interview.service.evaluation.EvaluationService;
import com.aicoach.interview.service.round.BaseRound;
import com.aicoach.interview.service.round.InterviewRound;
import com.aicoach.interview.service.stream.SessionEventStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Runs {@link Effect}s on virtual threads after their transition committed, and reports the outcome back to the
 * engine as an internal command. Interviewer messages are streamed token-by-token to the session's event stream.
 */
@Component
public class EffectRunner {

    private static final Logger log = LoggerFactory.getLogger(EffectRunner.class);
    private static final int FLUSH_CHARS = 24;
    private static final long FLUSH_MILLIS = 60;

    private final AiGateway ai;
    private final SessionEventStream stream;
    private final EvaluationService evaluations;
    private final ExecutorService executor;
    private volatile InterviewEngine engine;

    public EffectRunner(AiGateway ai, SessionEventStream stream, EvaluationService evaluations,
            @Qualifier("interviewExecutor") ExecutorService executor) {
        this.ai = ai;
        this.stream = stream;
        this.evaluations = evaluations;
        this.executor = executor;
    }

    void bind(InterviewEngine engine) {
        this.engine = engine;
    }

    public void submit(UUID sessionId, Effect effect) {
        executor.submit(() -> run(sessionId, effect));
    }

    /** Runs synchronously (used by the executor and by tests). */
    void run(UUID sessionId, Effect effect) {
        try {
            switch (effect) {
                case Effect.Generate g -> generate(sessionId, g);
                case Effect.GenerateHint h -> hint(sessionId, h);
                case Effect.Assess a -> assess(sessionId);
                case Effect.Evaluate e -> evaluate(sessionId);
            }
        } catch (AppException e) {
            if (e.errorCode() == ErrorCode.INVALID_TRANSITION || e.errorCode() == ErrorCode.NOT_FOUND) {
                log.info("Effect {} for session {} is obsolete: {}", effect, sessionId, e.getMessage());
            } else {
                log.warn("Effect {} for session {} failed: {}", effect, sessionId, e.getMessage());
                safeFail(sessionId, e.errorCode().name(), e.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("Effect {} for session {} crashed", effect, sessionId, e);
            safeFail(sessionId, "INTERNAL_ERROR", "Something went wrong. Please retry.");
        }
    }

    private void generate(UUID sessionId, Effect.Generate g) {
        SessionContext ctx = engine.loadContext(sessionId);
        InterviewRound round = ctx.round();
        TurnIntent intent = g.kind() == TurnKind.FOLLOW_UP ? TurnIntent.FOLLOW_UP : TurnIntent.ASK;
        Map<String, Object> vars = round.interviewerVariables(ctx, intent, g.focus());
        UUID turnId = UUID.randomUUID();
        stream.publish(sessionId, "turn.started", Map.of("turnId", turnId, "kind", g.kind(), "role", TurnRole.INTERVIEWER));
        TokenBuffer buffer = new TokenBuffer(sessionId, turnId);
        String text;
        try {
            text = ai.stream("interviewer", vars, callContext(ctx, AiPurpose.INTERVIEWER_TURN), buffer::append).text();
            buffer.flush();
        } catch (AiException e) {
            if (e.errorCode() == ErrorCode.TOKEN_BUDGET_EXCEEDED) {
                engine.dispatch(sessionId, null, new SessionCommand.BudgetExhausted());
                return;
            }
            engine.dispatch(sessionId, null, new SessionCommand.GenerationFailed(e.errorCode().name(),
                    "The interviewer could not respond right now. Retry in a moment."));
            return;
        }
        engine.dispatch(sessionId, null, new SessionCommand.TurnDelivered(turnId, g.kind(), text.trim()));
    }

    private void hint(UUID sessionId, Effect.GenerateHint h) {
        SessionContext ctx = engine.loadContext(sessionId);
        String seed = ctx.round().seedHint(ctx, h.level());
        Map<String, Object> vars = new HashMap<>();
        vars.put("level", h.level());
        vars.put("questionTitle", ctx.current().getTitle());
        vars.put("seedHint", seed == null ? "" : seed);
        vars.put("previousHints", ctx.turnsForCurrent().stream().filter(t -> t.getKind() == TurnKind.HINT)
                .map(Turn::getContent).collect(Collectors.joining(" | ")));
        vars.put("conversation", ctx.turnsForCurrent().stream().filter(t -> t.getRole() != TurnRole.INTERVIEWER)
                .map(Turn::getContent).collect(Collectors.joining("\n\n")));
        UUID turnId = UUID.randomUUID();
        String text;
        try {
            text = ai.complete("hint", vars, callContext(ctx, AiPurpose.HINT)).text().trim();
        } catch (AiException e) {
            if (seed == null || seed.isBlank()) {
                engine.dispatch(sessionId, null, new SessionCommand.GenerationFailed(e.errorCode().name(),
                        "The hint could not be generated right now. Retry in a moment."));
                return;
            }
            // the question bank's authored hint is a real (if less personalised) answer to the request
            text = seed;
        }
        engine.dispatch(sessionId, null, new SessionCommand.TurnDelivered(turnId, TurnKind.HINT, text));
    }

    private void assess(UUID sessionId) {
        SessionContext ctx = engine.loadContext(sessionId);
        TurnAssessment assessment = ctx.round().assess(ctx);
        engine.dispatch(sessionId, null, new SessionCommand.Decide(assessment));
    }

    private void evaluate(UUID sessionId) {
        boolean ok = evaluations.evaluate(sessionId);
        engine.dispatch(sessionId, null, new SessionCommand.EvaluationFinished(ok));
    }

    private void safeFail(UUID sessionId, String code, String message) {
        try {
            engine.dispatch(sessionId, null, new SessionCommand.GenerationFailed(code, message));
        } catch (RuntimeException ignored) {
            // the session is not in a state that tracks failures (e.g. already ended); nothing to undo
            stream.publish(sessionId, "error", Map.of("code", code, "message", message, "retryable", false));
        }
    }

    private static CallContext callContext(SessionContext ctx, AiPurpose purpose) {
        return ctx.round() instanceof BaseRound base ? base.callContext(ctx, purpose)
                : new CallContext(ctx.session().getUserId(), ctx.session().getId(), purpose, ctx.session().getSeed());
    }

    /** Coalesces streamed deltas into small batches so each SSE event carries a few words, not one character. */
    private final class TokenBuffer {
        private final UUID sessionId;
        private final UUID turnId;
        private final StringBuilder pending = new StringBuilder();
        private long lastFlush = System.nanoTime();

        TokenBuffer(UUID sessionId, UUID turnId) {
            this.sessionId = sessionId;
            this.turnId = turnId;
        }

        void append(String delta) {
            pending.append(delta);
            if (pending.length() >= FLUSH_CHARS || (System.nanoTime() - lastFlush) / 1_000_000 >= FLUSH_MILLIS) {
                flush();
            }
        }

        void flush() {
            if (!pending.isEmpty()) {
                stream.publish(sessionId, "token", Map.of("turnId", turnId, "delta", pending.toString()));
                pending.setLength(0);
            }
            lastFlush = System.nanoTime();
        }
    }
}
