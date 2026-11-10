package com.aicoach.interview.service.engine;

import com.aicoach.ai.schema.TurnAssessment;
import com.aicoach.interview.domain.InterviewEnums.InputMode;
import com.aicoach.interview.domain.InterviewEnums.TurnKind;
import java.util.UUID;

/** Everything that can happen to a session: user commands plus internal completions of asynchronous steps. */
public sealed interface SessionCommand {

    // ---- user commands
    record Start() implements SessionCommand {
    }

    record SubmitAnswer(UUID clientMessageId, String content, InputMode inputMode, String code, String language,
            String mermaid) implements SessionCommand {
    }

    record RequestHint(int level) implements SessionCommand {
    }

    record RetryTurn() implements SessionCommand {
    }

    record EndEarly() implements SessionCommand {
    }

    record Abandon() implements SessionCommand {
    }

    // ---- internal (from async effects and the sweeper)
    record TurnDelivered(UUID turnId, TurnKind kind, String content) implements SessionCommand {
    }

    record GenerationFailed(String code, String message) implements SessionCommand {
    }

    record Decide(TurnAssessment assessment) implements SessionCommand {
    }

    record Timeout() implements SessionCommand {
    }

    record BudgetExhausted() implements SessionCommand {
    }

    record EvaluationFinished(boolean succeeded) implements SessionCommand {
    }

    /** A code run/submit result, appended to the transcript so the interviewer and evaluator can see it. */
    record CodeRan(String summary, com.fasterxml.jackson.databind.JsonNode metadata) implements SessionCommand {
    }

    /** Client integrity signals (large pastes, tab switches): context in the report, never punitive. */
    record RecordIntegrity(java.util.List<String> signals) implements SessionCommand {
    }

    default boolean internal() {
        return this instanceof TurnDelivered || this instanceof GenerationFailed || this instanceof Decide
                || this instanceof Timeout || this instanceof BudgetExhausted || this instanceof EvaluationFinished;
    }
}
