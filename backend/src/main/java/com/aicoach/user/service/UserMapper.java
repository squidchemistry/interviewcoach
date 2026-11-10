package com.aicoach.user.service;

import com.aicoach.user.domain.Company;
import com.aicoach.user.domain.Profile;
import com.aicoach.user.domain.User;
import com.aicoach.user.dto.AdminUserResponse;
import com.aicoach.user.dto.CompanyResponse;
import com.aicoach.user.dto.ProfileResponse;
import com.aicoach.user.dto.UserResponse;
import java.util.Comparator;

final class UserMapper {

    private UserMapper() {
    }

    static UserResponse toResponse(User user, Profile profile) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getPlan(),
                user.isEmailVerified(), user.getPasswordHash() != null,
                profile == null ? null : toResponse(profile), user.getCreatedAt());
    }

    static ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(profile.getTargetRole(), profile.getExperienceYears(),
                profile.getPreferredLanguage(),
                profile.getTargetCompanies().stream()
                        .sorted(Comparator.comparing(Company::getName))
                        .map(UserMapper::toResponse)
                        .toList(),
                profile.getOnboardedAt(), profile.getUpdatedAt());
    }

    static CompanyResponse toResponse(Company company) {
        return new CompanyResponse(company.getId(), company.getName(), company.getSlug());
    }

    static AdminUserResponse toAdminResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getPlan(),
                user.isEmailVerified(), user.getCreatedAt());
    }
}
