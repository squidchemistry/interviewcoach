package com.aicoach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicoach.support.AuthTestClient;
import com.aicoach.support.AuthTestClient.Session;
import com.aicoach.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/** US-A1 / US-A2: registration, login, refresh rotation with reuse detection, logout, lockout. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AuthFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    AuthTestClient auth;

    @BeforeEach
    void setUp() {
        auth = new AuthTestClient(mvc);
    }

    @Test
    void registerLoginMeRefreshLogoutHappyPath(CapturedOutput output) throws Exception {
        String email = AuthTestClient.uniqueEmail();
        MockHttpServletResponse registered = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + AuthTestClient.PASSWORD
                                + "\",\"name\":\"Asha\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.hasPassword").value(true))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("aic_rt="), containsString("HttpOnly"), containsString("Secure"),
                        containsString("SameSite=Strict"), containsString("Path=/api/v1/auth"),
                        containsString("Max-Age=604800"))))
                .andReturn().getResponse();
        assertThat(registered.getContentAsString()).doesNotContain(AuthTestClient.PASSWORD);

        Session session = auth.login(email, AuthTestClient.PASSWORD);
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.profile").doesNotExist());

        // Refresh rotates the cookie and returns a fresh access token.
        MockHttpServletResponse refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse();
        String rotated = AuthTestClient.refreshCookieValue(refreshed);
        assertThat(rotated).isNotBlank().isNotEqualTo(session.refreshToken());

        // Logout revokes the family; the rotated cookie no longer works.
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(AuthTestClient.COOKIE, rotated)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(AuthTestClient.COOKIE, rotated)))
                .andExpect(status().isUnauthorized());

        assertThat(output.getAll()).doesNotContain(AuthTestClient.PASSWORD);
    }

    @Test
    void reusingARotatedRefreshTokenOutsideTheGraceWindowRevokesTheWholeFamily() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail());
        String second = AuthTestClient.refreshCookieValue(mvc.perform(post("/api/v1/auth/refresh")
                .cookie(session.cookie())).andExpect(status().isOk()).andReturn().getResponse());

        // Simulate an attacker replaying the first token long after it was rotated.
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() - interval '5 minutes' "
                + "WHERE user_id = ? AND replaced_by_id IS NOT NULL", session.userId());

        mvc.perform(post("/api/v1/auth/refresh").cookie(session.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        // ...which also kills the legitimate, newer token in the same family.
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(AuthTestClient.COOKIE, second)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, session.userId())).isZero();
    }

    @Test
    void concurrentTabRaceWithinGraceWindowDoesNotRevokeTheFamily() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail());
        String second = AuthTestClient.refreshCookieValue(mvc.perform(post("/api/v1/auth/refresh")
                .cookie(session.cookie())).andExpect(status().isOk()).andReturn().getResponse());

        mvc.perform(post("/api/v1/auth/refresh").cookie(session.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(AuthTestClient.COOKIE, second)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRejectsUntrustedOrigin() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail());
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.cookie()).header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.cookie()).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshIgnoresAStaleBearerHeader() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail());
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.cookie())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        auth.register(email);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  " + email.toUpperCase() + " \",\"password\":\"Another1Passw0rd\",\"name\":\"X\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"onlyletters\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].path").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "body.email", "body.password", "body.name")));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"short1\",\"name\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].path").value("body.password"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        auth.register(email);
        for (String candidate : List.of(email, AuthTestClient.uniqueEmail())) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + candidate + "\",\"password\":\"Wrong-passw0rd\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
        }
    }

    @Test
    void sixthFailedLoginIsLockedOutEvenWithTheRightPassword() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        auth.register(email);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"Wrong-passw0rd\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + AuthTestClient.PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        auth.register(email);
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 4; i++) {
                mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Wrong-passw0rd\"}"));
            }
            auth.login(email, AuthTestClient.PASSWORD);
        }
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void expiredAccessTokenReportsTokenExpired() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail());
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ai-interview-coach").audience(List.of("ai-interview-coach-web"))
                .subject(session.userId().toString()).issuedAt(past).expiresAt(past.plusSeconds(900))
                .claim("role", "USER").build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).keyId("k1").build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void tokenFromAnotherAudienceIsRejected() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ai-interview-coach").audience(List.of("some-other-app"))
                .subject(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("role", "ADMIN").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).keyId("k1").build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void providersReportGoogleDisabledWhenNotConfigured() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value(true))
                .andExpect(jsonPath("$.google").value(false));
        mvc.perform(get("/api/v1/auth/google"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void passwordsAreStoredAsArgon2Hashes() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail());
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, session.userId());
        assertThat(hash).startsWith("$argon2id$").doesNotContain(AuthTestClient.PASSWORD);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens WHERE user_id = ? LIMIT 1",
                String.class, session.userId())).hasSize(64).isNotEqualTo(session.refreshToken());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(content().string(not(containsString("argon2"))));
    }
}
