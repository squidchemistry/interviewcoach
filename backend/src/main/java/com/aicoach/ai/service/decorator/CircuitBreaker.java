package com.aicoach.ai.service.decorator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Count-based circuit breaker. CLOSED: calls flow; once at least {@code minimumCalls} of the last {@code window}
 * calls were recorded and the failure rate reaches {@code failureRateThreshold}, it OPENs and rejects calls for
 * {@code openDuration}. Then HALF_OPEN lets one trial call through: success closes it, failure re-opens it.
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int window;
    private final int minimumCalls;
    private final double failureRateThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private final boolean[] outcomes;
    private int recorded;
    private int next;
    private State state = State.CLOSED;
    private Instant openedAt;
    private boolean trialInFlight;

    public CircuitBreaker(int window, int minimumCalls, double failureRateThreshold, Duration openDuration, Clock clock) {
        this.window = window;
        this.minimumCalls = minimumCalls;
        this.failureRateThreshold = failureRateThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
        this.outcomes = new boolean[window];
    }

    /** Whether a call may proceed now; in HALF_OPEN only one trial call is admitted. */
    public synchronized boolean tryAcquire() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            state = State.HALF_OPEN;
            trialInFlight = false;
        }
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> {
                if (trialInFlight) {
                    yield false;
                }
                trialInFlight = true;
                yield true;
            }
        };
    }

    public synchronized void onSuccess() {
        if (state == State.HALF_OPEN) {
            reset();
            return;
        }
        record(false);
    }

    public synchronized void onFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        record(true);
        if (recorded >= minimumCalls && failureRate() >= failureRateThreshold) {
            open();
        }
    }

    public synchronized State state() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            return State.HALF_OPEN;
        }
        return state;
    }

    private void record(boolean failure) {
        outcomes[next] = failure;
        next = (next + 1) % window;
        recorded = Math.min(recorded + 1, window);
    }

    private double failureRate() {
        int failures = 0;
        for (int i = 0; i < recorded; i++) {
            if (outcomes[i]) {
                failures++;
            }
        }
        return (double) failures / recorded;
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.instant();
        trialInFlight = false;
    }

    private void reset() {
        state = State.CLOSED;
        recorded = 0;
        next = 0;
        trialInFlight = false;
    }
}
