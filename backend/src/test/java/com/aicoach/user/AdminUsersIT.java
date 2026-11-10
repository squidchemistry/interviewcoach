package com.aicoach.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicoach.support.AuthTestClient;
import com.aicoach.support.AuthTestClient.Session;
import com.aicoach.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** US-A5: admin-only routes and role management; demo seed data. */
@IntegrationTest
class AdminUsersIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    private AuthTestClient auth;

    @BeforeEach
    void setUp() {
        auth = new AuthTestClient(mvc);
    }

    private Session admin() throws Exception {
        return auth.login("admin@aicoach.dev", "AdminPassw0rd");
    }

    /** Every route under /api/v1/admin — discovered from the handler mappings, so new ones are covered too. */
    private List<Map.Entry<HttpMethod, String>> adminRoutes() {
        List<Map.Entry<HttpMethod, String>> routes = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            for (String pattern : entry.getKey().getPatternValues()) {
                if (!pattern.startsWith("/api/v1/admin")) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                for (RequestMethod method : entry.getKey().getMethodsCondition().getMethods()) {
                    routes.add(Map.entry(HttpMethod.valueOf(method.name()), path));
                }
            }
        }
        return routes;
    }

    @Test
    void everyAdminRouteIsForbiddenForUsersAndUnauthorizedForAnonymous() throws Exception {
        Session user = auth.register(AuthTestClient.uniqueEmail());
        List<Map.Entry<HttpMethod, String>> routes = adminRoutes();
        assertThat(routes).isNotEmpty();
        for (Map.Entry<HttpMethod, String> route : routes) {
            mvc.perform(request(route.getKey(), route.getValue()).header(HttpHeaders.AUTHORIZATION, user.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(request(route.getKey(), route.getValue())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void seededAccountsExistAndTheDemoCandidateHasAProfile() throws Exception {
        Session demo = auth.login("demo@aicoach.dev", "DemoPassw0rd");
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, demo.bearer()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.profile.targetRole").value("SDE_1"))
                .andExpect(jsonPath("$.profile.targetCompanies.length()").value(3));
        assertThat(admin().user().get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void adminSearchesUsers() throws Exception {
        String email = "searchable-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        auth.register(email, AuthTestClient.PASSWORD, "Findable Person");
        Session admin = admin();
        mvc.perform(get("/api/v1/admin/users").param("q", "findable person")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.email == '" + email + "')]").exists())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20));
        mvc.perform(get("/api/v1/admin/users").param("role", "ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.items[*].role").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("ADMIN"))));
        // LIKE wildcards in the query are literals, not patterns.
        mvc.perform(get("/api/v1/admin/users").param("q", "%").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/admin/users").param("pageSize", "500").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].path").value("pageSize"));
    }

    @Test
    void roleChangeIsAuditedAndAppliesAtTheNextRefresh() throws Exception {
        Session target = auth.register(AuthTestClient.uniqueEmail());
        Session admin = admin();

        mvc.perform(patch("/api/v1/admin/users/" + target.userId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT actor_id::text AS actor, action, before::text AS before, after::text AS after, request_id "
                        + "FROM audit_logs WHERE entity_id = ? ORDER BY id DESC LIMIT 1", target.userId());
        assertThat(audit.get("actor")).isEqualTo(admin.userId().toString());
        assertThat(audit.get("action")).isEqualTo("USER_ROLE_CHANGED");
        assertThat(audit.get("before").toString()).contains("USER");
        assertThat(audit.get("after").toString()).contains("ADMIN");
        assertThat(audit.get("request_id")).isNotNull();

        // The old access token still says USER; the refreshed one carries ADMIN.
        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, target.bearer()))
                .andExpect(status().isForbidden());
        String refreshed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                mvc.perform(post("/api/v1/auth/refresh").cookie(target.cookie())).andReturn().getResponse()
                        .getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshed))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotChangeOwnRoleAndUnknownUsersAre404() throws Exception {
        Session admin = admin();
        mvc.perform(patch("/api/v1/admin/users/" + admin.userId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(patch("/api/v1/admin/users/" + UUID.randomUUID() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/users/" + UUID.randomUUID() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].path").value("body.role"));
    }
}
