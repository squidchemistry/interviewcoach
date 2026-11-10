package com.aicoach.interview.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Public report links. Only the SHA-256 of the token is stored; the token itself is shown once. */
@Repository
public class ShareLinkRepository {

    private final JdbcTemplate jdbc;

    public ShareLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ShareLink(UUID id, UUID sessionId, Instant expiresAt, Instant revokedAt, int viewCount,
            Instant createdAt) {
    }

    public ShareLink insert(UUID sessionId, UUID createdBy, String tokenHash, Instant expiresAt) {
        return jdbc.queryForObject("""
                INSERT INTO shared_reports (session_id, token_hash, created_by, expires_at) VALUES (?, ?, ?, ?)
                RETURNING id, session_id, expires_at, revoked_at, view_count, created_at
                """, (rs, i) -> row(rs), sessionId, tokenHash, createdBy, Timestamp.from(expiresAt));
    }

    public List<ShareLink> forSession(UUID sessionId) {
        return jdbc.query("""
                SELECT id, session_id, expires_at, revoked_at, view_count, created_at FROM shared_reports
                WHERE session_id = ? ORDER BY created_at DESC
                """, (rs, i) -> row(rs), sessionId);
    }

    public int revokeAll(UUID sessionId, Instant now) {
        return jdbc.update("UPDATE shared_reports SET revoked_at = ? WHERE session_id = ? AND revoked_at IS NULL",
                Timestamp.from(now), sessionId);
    }

    /** The session behind a valid (unexpired, unrevoked) token; counts the view. */
    public Optional<UUID> resolve(String tokenHash, Instant now) {
        return jdbc.query("""
                UPDATE shared_reports SET view_count = view_count + 1
                WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
                RETURNING session_id
                """, (rs, i) -> rs.getObject(1, UUID.class), tokenHash, Timestamp.from(now)).stream().findFirst();
    }

    private static ShareLink row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp revoked = rs.getTimestamp("revoked_at");
        return new ShareLink(rs.getObject("id", UUID.class), rs.getObject("session_id", UUID.class),
                rs.getTimestamp("expires_at").toInstant(), revoked == null ? null : revoked.toInstant(),
                rs.getInt("view_count"), rs.getTimestamp("created_at").toInstant());
    }
}
