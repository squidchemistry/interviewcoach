package com.aicoach.auth.dto;

import com.aicoach.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** Returned by register, login and refresh. The refresh token travels only in the httpOnly cookie. */
public record AuthResponse(
        UserResponse user,
        @Schema(description = "Short-lived JWT; send as `Authorization: Bearer <token>`. Keep in memory only.")
        String accessToken,
        @Schema(description = "Access token lifetime in seconds") long expiresIn) {

    @Override
    public String toString() {
        return "AuthResponse[user=" + user.id() + ", accessToken=***, expiresIn=" + expiresIn + "]";
    }
}
