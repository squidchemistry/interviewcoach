package com.aicoach.common.security;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Extracts the caller's user id from a validated access token. */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static UUID id(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Authentication is required");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Malformed access token subject");
        }
    }
}
