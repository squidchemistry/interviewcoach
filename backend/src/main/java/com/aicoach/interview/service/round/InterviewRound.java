package com.aicoach.interview.service.round;

import com.aicoach.ai.schema.TurnAssessment;
import com.aicoach.interview.domain.InterviewEnums.InterviewMode;
import com.aicoach.interview.domain.InterviewEnums.TurnIntent;
import com.aicoach.interview.service.engine.SessionContext;
import com.aicoach.question.domain.QuestionEnums.Difficulty;
import java.util.Map;

/**
 * Strategy: everything that differs between DSA, LLD, behavioural and resume deep-dive rounds — question choice,
 * interviewer prompt variables, answer assessment and follow-up policy. The engine (State pattern) is shared.
 */
public interface InterviewRound {

    InterviewMode mode();

    PlannedQuestion pickQuestion(SessionContext ctx, Difficulty difficulty, int position);

    Map<String, Object> interviewerVariables(SessionContext ctx, TurnIntent intent, String focus);

    TurnAssessment assess(SessionContext ctx);

    NextAction decideNext(SessionContext ctx, TurnAssessment assessment);

    /** Seed hint for {@code level} (DSA only); others reject hints. */
    default String seedHint(SessionContext ctx, int level) {
        return null;
    }

    default boolean supportsHints() {
        return false;
    }
}
