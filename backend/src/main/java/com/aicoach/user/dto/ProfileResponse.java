package com.aicoach.user.dto;

import com.aicoach.user.domain.CodeLanguage;
import com.aicoach.user.domain.TargetRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProfileResponse(
        TargetRole targetRole,
        BigDecimal experienceYears,
        CodeLanguage preferredLanguage,
        List<CompanyResponse> targetCompanies,
        Instant onboardedAt,
        Instant updatedAt) {
}
