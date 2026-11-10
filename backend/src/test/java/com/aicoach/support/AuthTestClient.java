package com.aicoach.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Registers / logs in users through the real HTTP endpoints and exposes their tokens for later requests. */
public final class AuthTestClient {

    public static final String COOKIE = "aic_rt";
    public static final String PASSWORD = "Sup3rSecretPass";

    private final MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    public AuthTestClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** A logged-in session: bearer token, refresh-token cookie value and the user JSON. */
    public record Session(String accessToken, String refreshToken, JsonNode user) {
        public String bearer() {
            return "Bearer " + accessToken;
        }

        public Cookie cookie() {
            return new Cookie(COOKIE, refreshToken);
        }

        public UUID userId() {
            return UUID.fromString(user.get("id").asText());
        }
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    public Session register(String email) throws Exception {
        return register(email, PASSWORD, "Test User");
    }

    public Session register(String email, String password, String name) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password, "name", name))))
                .andReturn().getResponse();
        return session(response, 201);
    }

    public Session login(String email, String password) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andReturn().getResponse();
        return session(response, 200);
    }

    private Session session(MockHttpServletResponse response, int expectedStatus) throws Exception {
        if (response.getStatus() != expectedStatus) {
            throw new AssertionError("Expected " + expectedStatus + " but got " + response.getStatus() + ": "
                    + response.getContentAsString());
        }
        JsonNode body = json.readTree(response.getContentAsString());
        return new Session(body.get("accessToken").asText(), refreshCookieValue(response), body.get("user"));
    }

    public static String refreshCookieValue(MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        if (header == null || !header.startsWith(COOKIE + "=")) {
            return null;
        }
        return header.substring(COOKIE.length() + 1, header.indexOf(';'));
    }
}
