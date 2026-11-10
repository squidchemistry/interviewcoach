package com.aicoach.interview.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicoach.interview.repository.InterviewJdbcRepository.TopicStat;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SpacedRepetitionTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void goodAnswersGrowTheIntervalOneSixThenEase() {
        TopicStat first = SpacedRepetition.next("graphs", null, 8.0, NOW);
        assertThat(first.intervalDays()).isEqualTo(1);
        assertThat(first.repetitions()).isEqualTo(1);
        assertThat(first.dueAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));

        TopicStat second = SpacedRepetition.next("graphs", first, 8.0, NOW);
        assertThat(second.intervalDays()).isEqualTo(6);
        TopicStat third = SpacedRepetition.next("graphs", second, 10.0, NOW);
        assertThat(third.intervalDays()).isEqualTo((int) Math.round(6 * second.ease()));
        assertThat(third.attempts()).isEqualTo(3);
        assertThat(third.avgScore()).isEqualTo(8.67);
    }

    @Test
    void poorAnswersResetAndLowerEaseButNotBelowTheFloor() {
        TopicStat s = SpacedRepetition.next("dp", null, 9.0, NOW);
        s = SpacedRepetition.next("dp", s, 9.0, NOW);
        TopicStat failed = SpacedRepetition.next("dp", s, 2.0, NOW);
        assertThat(failed.repetitions()).isZero();
        assertThat(failed.intervalDays()).isEqualTo(1);
        assertThat(failed.ease()).isLessThan(s.ease());
        for (int i = 0; i < 20; i++) {
            failed = SpacedRepetition.next("dp", failed, 0.0, NOW);
        }
        assertThat(failed.ease()).isEqualTo(SpacedRepetition.MIN_EASE);
    }

    @Test
    void qualityMapsTheTenPointScaleToSmTwo() {
        assertThat(SpacedRepetition.quality(0)).isZero();
        assertThat(SpacedRepetition.quality(5.9)).isEqualTo(3);
        assertThat(SpacedRepetition.quality(10)).isEqualTo(5);
        assertThat(SpacedRepetition.quality(42)).isEqualTo(5);
    }
}
