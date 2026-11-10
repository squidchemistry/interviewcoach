package com.aicoach.user.service;

import com.aicoach.common.config.AppProperties;
import com.aicoach.user.domain.CodeLanguage;
import com.aicoach.user.domain.Role;
import com.aicoach.user.domain.TargetRole;
import com.aicoach.user.domain.User;
import com.aicoach.user.dto.UpdateProfileRequest;
import com.aicoach.user.repository.CompanyRepository;
import com.aicoach.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the demo candidate and an admin account on startup when {@code app.seed.enabled=true} (local / demo
 * environments only). Idempotent: existing accounts are left untouched, including their passwords.
 * Passwords come from configuration, never from source code.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_EMAIL = "demo@aicoach.dev";
    public static final String ADMIN_EMAIL = "admin@aicoach.dev";

    private final AppProperties.Seed config;
    private final UserService userService;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(AppProperties properties, UserService userService, UserRepository users,
            CompanyRepository companies, PasswordEncoder passwordEncoder) {
        this.config = properties.seed();
        this.userService = userService;
        this.users = users;
        this.companies = companies;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!config.enabled()) {
            return;
        }
        if (!StringUtils.hasText(config.demoPassword()) || !StringUtils.hasText(config.adminPassword())) {
            throw new IllegalStateException(
                    "app.seed.enabled=true requires DEMO_USER_PASSWORD and ADMIN_PASSWORD to be set");
        }
        seedDemoCandidate();
        seedAdmin();
    }

    private void seedDemoCandidate() {
        if (users.existsByEmail(DEMO_EMAIL)) {
            return;
        }
        User demo = userService.createPasswordUser(DEMO_EMAIL, "Priya (Demo)", passwordEncoder.encode(config.demoPassword()));
        List<UUID> targets = List.of("amazon", "google", "flipkart").stream()
                .map(companies::findBySlug).flatMap(Optional::stream).map(c -> c.getId()).toList();
        userService.updateProfile(demo.getId(),
                new UpdateProfileRequest(TargetRole.SDE_1, BigDecimal.ZERO, CodeLanguage.PYTHON, targets));
        log.info("Seeded demo candidate {}", DEMO_EMAIL);
    }

    private void seedAdmin() {
        if (users.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        User admin = userService.createPasswordUser(ADMIN_EMAIL, "Admin", passwordEncoder.encode(config.adminPassword()));
        admin.changeRole(Role.ADMIN);
        log.info("Seeded admin {}", ADMIN_EMAIL);
    }
}
