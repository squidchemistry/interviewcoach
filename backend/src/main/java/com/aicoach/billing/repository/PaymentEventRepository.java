package com.aicoach.billing.repository;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Inbox of provider webhooks; the unique event id makes processing idempotent. */
@Repository
public class PaymentEventRepository {

    private final JdbcTemplate jdbc;

    public PaymentEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Stores the event; false if this event id was already received (a redelivery). */
    public boolean insertIfNew(String provider, String eventId, String eventType, String payloadJson) {
        return jdbc.update("""
                INSERT INTO payment_events (provider, event_id, event_type, payload) VALUES (?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (event_id) DO NOTHING
                """, provider, eventId, eventType, payloadJson) == 1;
    }

    public void markProcessed(String eventId, Instant at, String error) {
        jdbc.update("UPDATE payment_events SET processed_at = ?, processing_error = ? WHERE event_id = ?",
                Timestamp.from(at), error, eventId);
    }
}
