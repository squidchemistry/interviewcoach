package com.aicoach.user.dto;

import com.aicoach.user.domain.Plan;
import com.aicoach.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.lang.Nullable;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        Role role,
        Plan plan,
        boolean emailVerified,
        @Schema(description = "False for accounts that only sign in with Google") boolean hasPassword,
        @Nullable @Schema(description = "Null until the candidate completes onboarding") ProfileResponse profile,
        Instant createdAt) {
}
