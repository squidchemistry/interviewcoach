package com.aicoach.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated application configuration ({@code app.*}). The application refuses to start
 * when any value is missing or invalid, so misconfiguration fails fast at boot rather than at runtime.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull @Valid Cors cors,
        @NotNull @Valid Http http,
        @NotNull @Valid Web web,
        @NotNull @Valid Auth auth,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull @Valid Seed seed) {

    private static final String ORIGIN_REGEX = "^https?://[A-Za-z0-9.-]+(:\\d{1,5})?$";

    /**
     * CORS whitelist. Origins must be exact scheme://host[:port] values; wildcards are rejected
     * because the API allows credentials (refresh-token cookie).
     */
    public record Cors(
            @NotEmpty List<@NotNull @Pattern(regexp = ORIGIN_REGEX,
                    message = "must be an exact origin such as https://app.example.com") String> allowedOrigins,
            @Positive @Max(86_400) long maxAgeSeconds) {
    }

    /** HTTP boundary limits. */
    public record Http(@Positive long maxBodyBytes) {
    }

    /** Where the web app lives; OAuth callbacks redirect the browser back here. */
    public record Web(@NotBlank @Pattern(regexp = ORIGIN_REGEX, message = "must be an origin without a path")
            String baseUrl) {
    }

    public record Auth(
            @NotNull @Valid Jwt jwt,
            @NotNull @Valid Refresh refresh,
            @NotNull @Valid Cookie cookie,
            @NotNull @Valid Login login,
            @NotNull @Valid Google google) {
    }

    /** Access tokens: HS256 JWTs. {@code secret} is base64 and must decode to at least 32 bytes. */
    public record Jwt(
            @NotBlank(message = "is required (set JWT_SECRET)") String secret,
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotNull Duration accessTokenTtl,
            @NotBlank String keyId) {
    }

    /**
     * Refresh tokens: opaque, rotated on every use. {@code reuseGrace} tolerates two browser tabs refreshing
     * at the same moment without treating the loser as a stolen-token replay.
     */
    public record Refresh(@NotNull Duration ttl, @NotNull Duration reuseGrace) {
    }

    /** {@code secure=false} only for plain-http local development. */
    public record Cookie(@NotBlank String name, boolean secure) {
    }

    /** Brute-force protection: failures per (email, client IP) within the window before logins are refused. */
    public record Login(@Positive int maxFailures, @NotNull Duration lockout) {
    }

    /** Google OIDC. Disabled unless client id, secret and redirect URI are all configured. */
    public record Google(
            String clientId,
            String clientSecret,
            String redirectUri,
            @NotBlank String authorizationUri,
            @NotBlank String tokenUri,
            @NotBlank String jwkSetUri) {

        public boolean enabled() {
            return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret) && StringUtils.hasText(redirectUri);
        }
    }

    public record RateLimit(boolean enabled, @NotNull @Valid Bucket auth, @NotNull @Valid Bucket refresh,
            @NotNull @Valid Bucket api) {
    }

    /** Token bucket: {@code capacity} requests, refilled continuously over {@code period}. */
    public record Bucket(@Positive int capacity, @NotNull Duration period) {
    }

    /** Demo data for local runs and demos. Never enable in production. */
    public record Seed(boolean enabled, String demoPassword, String adminPassword) {
    }
}
