package com.aicoach.interview.service.round;

import com.aicoach.question.domain.QuestionEnums.Difficulty;

/** What the interviewer does after an answer has been assessed. */
public sealed interface NextAction {

    record FollowUp(String focus) implements NextAction {
    }

    record NextQuestion(Difficulty difficulty, String reason) implements NextAction {
    }

    record Finish() implements NextAction {
    }
}
