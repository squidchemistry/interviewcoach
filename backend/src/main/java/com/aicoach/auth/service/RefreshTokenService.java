package com.aicoach.auth.service;

import com.aicoach.auth.domain.RefreshToken;
import com.aicoach.auth.repository.RefreshTokenRepository;
import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.security.Hashing;
import com.aicoach.common.web.ClientInfo;
import com.aicoach.event.UserDeletedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation and reuse detection (OAuth 2.0 Security BCP §4.14):
 * every refresh returns a new token and revokes the old one. If a revoked token is presented again, someone may be
 * replaying a stolen token, so the whole family (every descendant of the original login) is revoked.
 * <p>
 * Exception: a token rotated within the last {@code reuse-grace} seconds is treated as a benign race (two tabs
 * refreshing at once). The request fails, but the family survives; the browser already holds the newer cookie.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final AppProperties.Refresh config;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, AppProperties properties, Clock clock) {
        this.tokens = tokens;
        this.config = properties.auth().refresh();
        this.clock = clock;
    }

    /** A newly minted token: {@code value} goes to the client once and is never stored. */
    public record IssuedToken(String value, UUID tokenId, UUID userId, UUID familyId, long maxAgeSeconds) {
        @Override
        public String toString() {
            return "IssuedToken[***, user=" + userId + ", family=" + familyId + "]";
        }
    }

    @Transactional
    public IssuedToken issueNewFamily(UUID userId, ClientInfo client) {
        return issue(userId, UUID.randomUUID(), client);
    }

    /** Validates and rotates {@code presented}; reuse of a rotated token revokes its family (committed even on failure). */
    @Transactional(noRollbackFor = AppException.class)
    public IssuedToken rotate(String presented, ClientInfo client) {
        if (presented == null || presented.isBlank()) {
            throw invalid("No refresh token");
        }
        RefreshToken current = tokens.findByTokenHashForUpdate(Hashing.sha256Hex(presented))
                .orElseThrow(() -> invalid("Unknown refresh token"));
        Instant now = clock.instant();
        if (current.isRevoked()) {
            boolean benignRace = current.getReplacedById() != null
                    && current.getRevokedAt().plus(config.reuseGrace()).isAfter(now);
            if (!benignRace) {
                int revoked = tokens.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected for user {}: revoked {} token(s) in family {}",
                        current.getUserId(), revoked, current.getFamilyId());
            }
            throw invalid("Refresh token has already been used");
        }
        if (current.isExpired(now)) {
            throw invalid("Refresh token expired");
        }
        IssuedToken next = issue(current.getUserId(), current.getFamilyId(), client);
        current.revoke(now, next.tokenId());
        return next;
    }

    /** Logout: revokes the presented token's whole family. Unknown tokens are ignored (logout is idempotent). */
    @Transactional
    public void revokeFamilyOf(String presented) {
        if (presented == null || presented.isBlank()) {
            return;
        }
        tokens.findByTokenHashForUpdate(Hashing.sha256Hex(presented))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    /** Observer: a deleted account must not be able to refresh. Runs inside the deleting transaction. */
    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        revokeAllForUser(event.userId());
    }

    private IssuedToken issue(UUID userId, UUID familyId, ClientInfo client) {
        String value = Hashing.randomToken(TOKEN_BYTES);
        Instant expiresAt = clock.instant().plus(config.ttl());
        RefreshToken saved = tokens.saveAndFlush(new RefreshToken(userId, familyId, Hashing.sha256Hex(value), expiresAt,
                client.userAgent(), client.ip()));
        return new IssuedToken(value, saved.getId(), userId, familyId, config.ttl().toSeconds());
    }

    private static AppException invalid(String detail) {
        return new AppException(ErrorCode.UNAUTHENTICATED, detail);
    }
}
