package com.aicoach.user.dto;

import com.aicoach.common.validation.HalfStep;
import com.aicoach.user.domain.CodeLanguage;
import com.aicoach.user.domain.TargetRole;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateProfileRequest(
        @NotNull TargetRole targetRole,
        @NotNull @DecimalMin("0") @DecimalMax("30") @HalfStep BigDecimal experienceYears,
        @NotNull CodeLanguage preferredLanguage,
        @NotNull @Size(max = 10) List<@NotNull UUID> targetCompanyIds) {
}
