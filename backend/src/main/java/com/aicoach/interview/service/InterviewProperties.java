package com.aicoach.interview.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.interview.*}: session lifecycle and free-plan quota. */
@ConfigurationProperties(prefix = "app.interview")
public record InterviewProperties(Duration inactivityTimeout, Duration staleProcessingAfter, int freeMonthlyInterviews) {

    public InterviewProperties {
        inactivityTimeout = inactivityTimeout == null ? Duration.ofMinutes(30) : inactivityTimeout;
        staleProcessingAfter = staleProcessingAfter == null ? Duration.ofMinutes(2) : staleProcessingAfter;
        freeMonthlyInterviews = freeMonthlyInterviews <= 0 ? 3 : freeMonthlyInterviews;
    }
}
