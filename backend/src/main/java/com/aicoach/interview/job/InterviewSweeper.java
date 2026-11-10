package com.aicoach.interview.job;

import com.aicoach.interview.repository.EvaluationRepository;
import com.aicoach.interview.repository.SessionRepository;
import com.aicoach.interview.service.InterviewProperties;
import com.aicoach.interview.service.engine.Effect;
import com.aicoach.interview.service.engine.EffectRunner;
import com.aicoach.interview.service.engine.InterviewEngine;
import com.aicoach.interview.service.engine.SessionCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recovers sessions whose background work was lost (process restart, crash) and ends abandoned ones:
 * <ul>
 *   <li>idle for the inactivity timeout → {@code Timeout} (evaluates what was answered);</li>
 *   <li>"processing" for too long → {@code GenerationFailed}, so the candidate gets a retry button;</li>
 *   <li>evaluations pending/running for too long → evaluation re-submitted (max 3 attempts).</li>
 * </ul>
 * Safe with several replicas: every action goes through the engine's optimistic locking, and a duplicate evaluation
 * only recomputes the same result.
 */
@Component
public class InterviewSweeper {

    private static final Logger log = LoggerFactory.getLogger(InterviewSweeper.class);
    static final Duration EVALUATION_STALL = Duration.ofMinutes(3);

    private final SessionRepository sessions;
    private final EvaluationRepository evaluations;
    private final InterviewEngine engine;
    private final EffectRunner effects;
    private final InterviewProperties properties;
    private final Clock clock;

    public InterviewSweeper(SessionRepository sessions, EvaluationRepository evaluations, InterviewEngine engine,
            EffectRunner effects, InterviewProperties properties, Clock clock) {
        this.sessions = sessions;
        this.evaluations = evaluations;
        this.engine = engine;
        this.effects = effects;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT20S")
    public void sweep() {
        Instant now = clock.instant();
        for (UUID id : sessions.findStuckProcessing(now.minus(properties.staleProcessingAfter()))) {
            safely(id, () -> engine.dispatch(id, null, new SessionCommand.GenerationFailed("STALLED",
                    "The interviewer stopped responding. Retry to continue.")));
        }
        for (UUID id : sessions.findIdle(now.minus(properties.inactivityTimeout()))) {
            safely(id, () -> engine.dispatch(id, null, new SessionCommand.Timeout()));
        }
        for (UUID id : evaluations.findStalled(now.minus(EVALUATION_STALL))) {
            log.info("Re-running stalled evaluation of session {}", id);
            effects.submit(id, new Effect.Evaluate());
        }
    }

    private static void safely(UUID id, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.info("Sweeper skipped session {}: {}", id, e.getMessage());
        }
    }
}
