package com.aicoach.user.dto;

import com.aicoach.user.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull Role role) {
}
