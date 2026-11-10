package com.aicoach.interview.service.engine;

import com.aicoach.interview.domain.InterviewEnums.TurnKind;

/** Asynchronous work requested by a state transition; executed only after the transition commits. */
public sealed interface Effect {

    /** Stream the interviewer's next message (a question or a follow-up). */
    record Generate(TurnKind kind, String focus) implements Effect {
    }

    record GenerateHint(int level) implements Effect {
    }

    /** Assess the latest answer, then decide: follow up, next question, or finish. */
    record Assess() implements Effect {
    }

    record Evaluate() implements Effect {
    }
}
