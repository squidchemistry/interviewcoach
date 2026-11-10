package com.aicoach.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicoach.auth.service.SafeRedirects;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityPrimitivesTest {

    @Test
    void pkceChallengeMatchesRfc7636AppendixB() {
        assertThat(Hashing.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void randomTokensAreUrlSafeAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = Hashing.randomToken(32);
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            seen.add(token);
        }
        assertThat(seen).hasSize(1000);
    }

    @Test
    void sha256HexAndConstantTimeEquals() {
        assertThat(Hashing.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Hashing.constantTimeEquals("a", "a")).isTrue();
        assertThat(Hashing.constantTimeEquals("a", "b")).isFalse();
        assertThat(Hashing.constantTimeEquals(null, "a")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/profile", "/admin/users?page=2", "/a-b_c.d~e%20f"})
    void safeRedirectsAcceptSameSitePaths(String path) {
        assertThat(SafeRedirects.isSafe(path)).isTrue();
        assertThat(SafeRedirects.sanitize(path)).isEqualTo(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.com", "//evil.com", "/\\evil.com", "evil.com", "/a//b", "/<script>", "javascript:alert(1)", ""})
    void safeRedirectsRejectOpenRedirectTricks(String path) {
        assertThat(SafeRedirects.isSafe(path)).isFalse();
        assertThat(SafeRedirects.sanitize(path)).isEqualTo("/");
    }

    @Test
    void jwtSecretMustBeBase64AndLongEnough() {
        assertThatThrownBy(() -> JwtConfig.signingKey("not base64 !!")).hasMessageContaining("base64");
        assertThatThrownBy(() -> JwtConfig.signingKey("c2hvcnQ=")).hasMessageContaining("at least 32 bytes");
        assertThat(JwtConfig.signingKey(JwtTestSupport.SECRET).getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
