package com.aicoach.interview.service.evaluation;

import com.aicoach.interview.repository.InterviewJdbcRepository.TopicStat;
import java.time.Duration;
import java.time.Instant;

/**
 * SM-2 spaced repetition per topic tag. A question's turn score (0-10) maps to SM-2 quality 0-5; weak topics come back
 * sooner, mastered ones drift out.
 */
public final class SpacedRepetition {

    static final double DEFAULT_EASE = 2.5;
    static final double MIN_EASE = 1.3;

    private SpacedRepetition() {
    }

    public static int quality(double score) {
        return (int) Math.round(Math.max(0, Math.min(10, score)) / 2.0);
    }

    /** New topic state after one more attempt scored {@code score} at {@code now}. {@code previous} may be null. */
    public static TopicStat next(String tag, TopicStat previous, double score, Instant now) {
        int q = quality(score);
        int attempts = previous == null ? 1 : previous.attempts() + 1;
        double avg = previous == null ? score : (previous.avgScore() * previous.attempts() + score) / attempts;
        double ease = previous == null ? DEFAULT_EASE : previous.ease();
        int repetitions = previous == null ? 0 : previous.repetitions();
        int interval = previous == null ? 0 : previous.intervalDays();
        if (q >= 3) {
            repetitions++;
            interval = switch (repetitions) {
                case 1 -> 1;
                case 2 -> 6;
                default -> (int) Math.round(Math.max(1, interval) * ease);
            };
        } else {
            repetitions = 0;
            interval = 1;
        }
        ease = Math.max(MIN_EASE, ease + 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        return new TopicStat(tag, attempts, Math.round(avg * 100) / 100.0, score, repetitions,
                Math.round(ease * 100) / 100.0, interval, now.plus(Duration.ofDays(interval)), now);
    }
}
