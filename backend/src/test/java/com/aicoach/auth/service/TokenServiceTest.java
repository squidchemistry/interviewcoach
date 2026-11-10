package com.aicoach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.security.JwtTestSupport;
import com.aicoach.user.domain.Role;
import com.aicoach.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static final AppProperties.Jwt CONFIG = new AppProperties.Jwt(
            JwtTestSupport.SECRET, "ai-interview-coach", "ai-interview-coach-web", Duration.ofMinutes(15), "k1");

    private static User user(Role role) {
        User user = User.withPassword("asha@example.com", "Asha", "hash");
        ReflectionTestUtils.setField(user, "id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.changeRole(role);
        return user;
    }

    private static TokenService serviceAt(Instant instant) {
        return new TokenService(JwtTestSupport.encoder(CONFIG), JwtTestSupport.properties(CONFIG),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuesAShortLivedTokenWithSubjectRoleAudienceAndKeyId() {
        TokenService.AccessToken token = serviceAt(NOW).issue(user(Role.ADMIN));
        assertThat(token.expiresInSeconds()).isEqualTo(900);

        Jwt jwt = JwtTestSupport.decoder(CONFIG, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC)).decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getAudience()).containsExactly("ai-interview-coach-web");
        assertThat(jwt.getHeaders()).containsEntry("kid", "k1").containsEntry("alg", "HS256");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(token.toString()).doesNotContain(token.value());
    }

    @Test
    void expiredTokensAreRejectedAfterClockSkew() {
        String value = serviceAt(NOW).issue(user(Role.USER)).value();
        JwtDecoder justInsideSkew = JwtTestSupport.decoder(CONFIG, Clock.fixed(NOW.plusSeconds(900 + 20), ZoneOffset.UTC));
        assertThat(justInsideSkew.decode(value).getSubject()).isNotNull();
        JwtDecoder afterSkew = JwtTestSupport.decoder(CONFIG, Clock.fixed(NOW.plusSeconds(900 + 31), ZoneOffset.UTC));
        assertThatThrownBy(() -> afterSkew.decode(value)).isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokensSignedWithAnotherSecretAreRejected() {
        AppProperties.Jwt other = new AppProperties.Jwt(JwtTestSupport.OTHER_SECRET, CONFIG.issuer(), CONFIG.audience(),
                CONFIG.accessTokenTtl(), "k1");
        String forged = new TokenService(JwtTestSupport.encoder(other), JwtTestSupport.properties(other),
                Clock.fixed(NOW, ZoneOffset.UTC)).issue(user(Role.ADMIN)).value();
        JwtDecoder decoder = JwtTestSupport.decoder(CONFIG, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> decoder.decode(forged)).hasMessageContaining("Signed JWT rejected");
    }
}
