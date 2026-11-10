package com.aicoach.user.service;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.error.FieldValidationException;
import com.aicoach.event.UserDeletedEvent;
import com.aicoach.user.domain.Company;
import com.aicoach.user.domain.Profile;
import com.aicoach.user.domain.User;
import com.aicoach.user.dto.ProfileResponse;
import com.aicoach.user.dto.UpdateProfileRequest;
import com.aicoach.user.dto.UserResponse;
import com.aicoach.user.repository.CompanyRepository;
import com.aicoach.user.repository.ProfileRepository;
import com.aicoach.user.repository.UserRepository;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Accounts and candidate profiles. The auth feature uses this service; it never touches user tables directly. */
@Service
public class UserService {

    private static final int MAX_NAME = 80;

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final CompanyRepository companies;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserService(UserRepository users, ProfileRepository profiles, CompanyRepository companies,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.companies = companies;
        this.events = events;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<User> findActiveByEmail(String email) {
        return users.findByEmail(normalizeEmail(email)).filter(u -> !u.isDeleted());
    }

    /** The authenticated user's account; a deleted or missing account invalidates the session. */
    @Transactional(readOnly = true)
    public User getActive(UUID userId) {
        return users.findById(userId).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "This account no longer exists"));
    }

    @Transactional
    public User createPasswordUser(String email, String name, String passwordHash) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new AppException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        try {
            return users.saveAndFlush(User.withPassword(normalized, name.trim(), passwordHash));
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration with the same email lost the race on the unique index.
            throw new AppException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
    }

    /**
     * Finds or creates the account for a Google sign-in. An existing password account is linked only when Google
     * asserts the email is verified; otherwise anyone could claim an account by creating a Google login for an
     * email address they do not control.
     */
    @Transactional
    public User resolveGoogleUser(GoogleIdentity identity) {
        Optional<User> bySub = users.findByGoogleSub(identity.subject()).filter(u -> !u.isDeleted());
        if (bySub.isPresent()) {
            return bySub.get();
        }
        if (!identity.emailVerified() || !StringUtils.hasText(identity.email())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Your Google account email is not verified");
        }
        String email = normalizeEmail(identity.email());
        Optional<User> byEmail = users.findByEmail(email).filter(u -> !u.isDeleted());
        if (byEmail.isPresent()) {
            byEmail.get().linkGoogle(identity.subject());
            return byEmail.get();
        }
        String name = StringUtils.hasText(identity.name()) ? identity.name().trim() : email.substring(0, email.indexOf('@'));
        if (name.length() > MAX_NAME) {
            name = name.substring(0, MAX_NAME);
        }
        return users.saveAndFlush(User.withGoogle(email, name, identity.subject()));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return toResponse(getActive(userId));
    }

    @Transactional(readOnly = true)
    public UserResponse toResponse(User user) {
        return UserMapper.toResponse(user, profiles.findWithCompaniesByUserId(user.getId()).orElse(null));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        getActive(userId);
        Set<Company> selected = resolveCompanies(request.targetCompanyIds());
        Profile profile = profiles.findWithCompaniesByUserId(userId).orElseGet(() -> new Profile(userId));
        profile.update(request.targetRole(), request.experienceYears(), request.preferredLanguage(), selected);
        return UserMapper.toResponse(profiles.saveAndFlush(profile));
    }

    private Set<Company> resolveCompanies(List<UUID> ids) {
        Set<UUID> unique = new LinkedHashSet<>(ids);
        List<Company> found = companies.findAllById(unique);
        if (found.size() != unique.size()) {
            throw new FieldValidationException("body.targetCompanyIds", "contains an unknown company id");
        }
        return new LinkedHashSet<>(found);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = getActive(userId);
        profiles.findById(userId).ifPresent(profiles::delete);
        user.anonymise(clock.instant());
        users.flush();
        events.publishEvent(new UserDeletedEvent(userId));
    }
}
