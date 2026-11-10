package com.aicoach.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicoach.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 1 acceptance: health, request ids, CORS, body limits, error format, security headers, API docs. */
@IntegrationTest
class ApiFoundationIT {

    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsUpWithAllReadinessComponents() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.components.db").value("UP"))
                .andExpect(jsonPath("$.components.redis").value("UP"))
                .andExpect(jsonPath("$.components.pgvector").value("UP"))
                .andExpect(jsonPath("$.components.readinessState").value("UP"));
    }

    @Test
    void flywayInstalledRequiredExtensions() {
        assertThat(jdbc.queryForList("SELECT extname FROM pg_extension", String.class))
                .contains("vector", "citext", "pgcrypto");
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1", "2", "3", "4", "5");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM companies", Integer.class)).isGreaterThanOrEqualTo(15);
    }

    @Test
    void requestIdIsEchoedOrGenerated() throws Exception {
        mvc.perform(get("/api/v1/health").header("X-Request-Id", "client-req-0001"))
                .andExpect(header().string("X-Request-Id", "client-req-0001"));
        mvc.perform(get("/api/v1/health"))
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void corsPreflightAllowsWhitelistedOrigin() throws Exception {
        mvc.perform(options("/api/v1/health")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void corsRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/v1/health")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(get("/api/v1/health").header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void oversizedBodyIsRejectedWith413ProblemDetail() throws Exception {
        byte[] body = new byte[102_401];
        mvc.perform(post("/api/v1/anything").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void unknownApiRouteRequiresAuthenticationFirst() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownApiRouteIs404ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").with(jwt()).header("X-Request-Id", "trace-abcdef12"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("trace-abcdef12"))
                .andExpect(jsonPath("$.instance").value("/api/v1/does-not-exist"));
    }

    @Test
    void pathsOutsideTheApiAreDeniedWithProblemDetail() throws Exception {
        mvc.perform(get("/internal/secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }

    @Test
    void openApiDocumentDescribesHealthEndpoint() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AI Interview Coach API"))
                .andExpect(jsonPath("$.paths['/api/v1/health'].get").exists());
        // Contract the generated frontend types rely on: records are fully required, @Nullable is `X | null`.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.components.schemas.UserResponse.required").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("profile"))))
                .andExpect(jsonPath("$.components.schemas.UserResponse.required").value(
                        org.hamcrest.Matchers.hasItems("id", "email", "role")))
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.profile.anyOf[1].type").value("null"))
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.profile.x-nullable").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
    }

    @Test
    void unknownRouteDetailDoesNotMentionFrameworkInternals() throws Exception {
        mvc.perform(get("/api/v1/nope").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(containsString("static resource"))));
    }
}
