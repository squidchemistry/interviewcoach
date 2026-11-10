package com.aicoach.user.dto;

import com.aicoach.user.domain.Plan;
import com.aicoach.user.domain.Role;
import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id, String email, String name, Role role, Plan plan, boolean emailVerified, Instant createdAt) {
}
