package com.aicoach.interview.service.round;

import com.aicoach.question.domain.QuestionEnums.Difficulty;
import org.springframework.stereotype.Component;

/**
 * Strategy for adaptive difficulty. Threshold policy: a turn score ≥ 7.5 steps up, ≤ 4.0 steps down, otherwise the
 * difficulty stays (clamped to EASY..HARD). The reason is stored and shown in the report.
 */
public interface DifficultyPolicy {

    record Decision(Difficulty difficulty, String reason) {
    }

    Decision next(Difficulty current, double turnScore);

    @Component
    class Threshold implements DifficultyPolicy {

        static final double UP = 7.5;
        static final double DOWN = 4.0;

        @Override
        public Decision next(Difficulty current, double turnScore) {
            String score = String.format(java.util.Locale.ROOT, "%.1f", turnScore);
            if (turnScore >= UP && current != Difficulty.HARD) {
                return new Decision(current.harder(), "Raised to " + current.harder() + " after a strong answer (" + score + "/10)");
            }
            if (turnScore <= DOWN && current != Difficulty.EASY) {
                return new Decision(current.easier(), "Lowered to " + current.easier() + " after a difficult answer (" + score + "/10)");
            }
            return new Decision(current, "Kept at " + current + " (previous answer " + score + "/10)");
        }
    }
}
