package com.aicoach.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicoach.common.security.Hashing;
import com.aicoach.support.AuthTestClient;
import com.aicoach.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** US-A3: Google OIDC with state + PKCE, against a WireMock "Google" that signs real RS256 ID tokens. */
@IntegrationTest
class GoogleOAuthIT {

    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
    private static final WireMockServer GOOGLE = new WireMockServer(options().dynamicPort());
    private static final RSAKey SIGNING_KEY;

    static {
        GOOGLE.start();
        try {
            SIGNING_KEY = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        GOOGLE.stubFor(get(urlPathEqualTo("/certs")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())));
    }

    @DynamicPropertySource
    static void googleProperties(DynamicPropertyRegistry registry) {
        registry.add("app.auth.google.client-id", () -> CLIENT_ID);
        registry.add("app.auth.google.client-secret", () -> "test-secret");
        registry.add("app.auth.google.redirect-uri", () -> "http://localhost:8088/api/v1/auth/google/callback");
        registry.add("app.auth.google.authorization-uri", () -> GOOGLE.baseUrl() + "/authorize");
        registry.add("app.auth.google.token-uri", () -> GOOGLE.baseUrl() + "/token");
        registry.add("app.auth.google.jwk-set-uri", () -> GOOGLE.baseUrl() + "/certs");
    }

    @AfterAll
    static void stopGoogle() {
        GOOGLE.stop();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void resetRequests() {
        GOOGLE.resetRequests();
    }

    private static String idToken(String sub, String email, boolean verified, String audience) throws Exception {
        Date now = new Date();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com").audience(audience).subject(sub)
                .claim("email", email).claim("email_verified", verified).claim("name", "Google Person")
                .issueTime(now).expirationTime(new Date(now.getTime() + 300_000)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID())
                .type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new RSASSASigner(SIGNING_KEY));
        return jwt.serialize();
    }

    private static void googleReturns(String idToken) {
        GOOGLE.stubFor(post(urlPathEqualTo("/token")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"ya29.test\",\"token_type\":\"Bearer\",\"id_token\":\"" + idToken + "\"}")));
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> params = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    /** Starts the flow and returns the parameters of the redirect to Google. */
    private Map<String, String> start(String redirect) throws Exception {
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/google")
                .param("redirect", redirect)).andExpect(status().isFound()).andReturn().getResponse();
        URI location = URI.create(response.getHeader(HttpHeaders.LOCATION));
        assertThat(location.toString()).startsWith(GOOGLE.baseUrl() + "/authorize");
        return query(location);
    }

    private MockHttpServletResponse callback(String state) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/google/callback")
                .param("code", "auth-code-123").param("state", state)).andReturn().getResponse();
    }

    @Test
    void newGoogleUserSignsInWithPkceAndLandsOnTheRequestedPage() throws Exception {
        Map<String, String> params = start("/profile");
        assertThat(params).containsEntry("client_id", CLIENT_ID)
                .containsEntry("response_type", "code")
                .containsEntry("scope", "openid email profile")
                .containsEntry("code_challenge_method", "S256")
                .containsKeys("state", "code_challenge");

        String email = "g-" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        googleReturns(idToken("sub-" + UUID.randomUUID(), email, true, CLIENT_ID));
        MockHttpServletResponse response = callback(params.get("state"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("http://localhost:5173/auth/complete?redirect=/profile");
        String refreshToken = AuthTestClient.refreshCookieValue(response);
        assertThat(refreshToken).isNotBlank();

        // PKCE: the verifier sent to Google hashes to the challenge sent in the browser redirect.
        List<LoggedRequest> exchanges = GOOGLE.findAll(postRequestedFor(urlPathEqualTo("/token")));
        assertThat(exchanges).hasSize(1);
        Map<String, String> form = query(URI.create("http://x/?" + exchanges.getFirst().getBodyAsString()));
        assertThat(Hashing.pkceChallenge(form.get("code_verifier"))).isEqualTo(params.get("code_challenge"));
        assertThat(form).containsEntry("code", "auth-code-123").containsEntry("grant_type", "authorization_code");

        // The web app completes sign-in by exchanging the cookie for an access token.
        JsonNode session = json.readTree(mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .cookie(new Cookie(AuthTestClient.COOKIE, refreshToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(session.at("/user/email").asText()).isEqualTo(email);
        assertThat(session.at("/user/emailVerified").asBoolean()).isTrue();
        assertThat(session.at("/user/hasPassword").asBoolean()).isFalse();
    }

    @Test
    void stateIsSingleUse() throws Exception {
        Map<String, String> params = start("/");
        googleReturns(idToken("sub-" + UUID.randomUUID(), "once-" + UUID.randomUUID() + "@gmail.com", true, CLIENT_ID));
        assertThat(callback(params.get("state")).getHeader(HttpHeaders.LOCATION)).contains("/auth/complete");
        assertThat(callback(params.get("state")).getHeader(HttpHeaders.LOCATION))
                .isEqualTo("http://localhost:5173/login?error=google_failed");
        assertThat(callback("forged-state").getHeader(HttpHeaders.LOCATION)).endsWith("error=google_failed");
    }

    @Test
    void verifiedGoogleEmailLinksAnExistingPasswordAccount() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        UUID existingId = new AuthTestClient(mvc).register(email).userId();
        String sub = "sub-" + UUID.randomUUID();

        Map<String, String> params = start("/");
        googleReturns(idToken(sub, email, true, CLIENT_ID));
        assertThat(callback(params.get("state")).getStatus()).isEqualTo(302);

        assertThat(jdbc.queryForObject("SELECT google_sub FROM users WHERE id = ?", String.class, existingId))
                .isEqualTo(sub);
    }

    @Test
    void unverifiedEmailIsNotTrustedForANewAccountOrForLinking() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        Map<String, String> params = start("/");
        googleReturns(idToken("sub-" + UUID.randomUUID(), email, false, CLIENT_ID));
        assertThat(callback(params.get("state")).getHeader(HttpHeaders.LOCATION)).endsWith("error=google_failed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    void idTokenForAnotherClientIsRejected() throws Exception {
        Map<String, String> params = start("/");
        googleReturns(idToken("sub-" + UUID.randomUUID(), AuthTestClient.uniqueEmail(), true, "someone-else"));
        assertThat(callback(params.get("state")).getHeader(HttpHeaders.LOCATION)).endsWith("error=google_failed");
    }

    @Test
    void tokenEndpointFailureRedirectsWithError() throws Exception {
        Map<String, String> params = start("/");
        GOOGLE.stubFor(post(urlPathEqualTo("/token")).willReturn(aResponse().withStatus(400)
                .withBody("{\"error\":\"invalid_grant\"}")));
        assertThat(callback(params.get("state")).getHeader(HttpHeaders.LOCATION)).endsWith("error=google_failed");
        GOOGLE.verify(postRequestedFor(urlPathEqualTo("/token")).withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.containing("client_secret=test-secret")));
    }

    @Test
    void userCancellingAtGoogleIsReportedAsCancelled() throws Exception {
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/google/callback")
                .param("error", "access_denied")).andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("http://localhost:5173/login?error=google_cancelled");
    }

    @Test
    void unsafeRedirectTargetsAreReplacedWithHome() throws Exception {
        for (String unsafe : List.of("//evil.example.com", "https://evil.example.com", "/\\evil.example.com")) {
            Map<String, String> params = start(unsafe);
            googleReturns(idToken("sub-" + UUID.randomUUID(), AuthTestClient.uniqueEmail(), true, CLIENT_ID));
            assertThat(callback(params.get("state")).getHeader(HttpHeaders.LOCATION))
                    .isEqualTo("http://localhost:5173/auth/complete?redirect=/");
        }
    }

    @Test
    void providersAdvertiseGoogle() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/providers")).andExpect(jsonPath("$.google").value(true));
    }
}
