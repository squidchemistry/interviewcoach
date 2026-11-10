package com.aicoach.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicoach.support.AuthTestClient;
import com.aicoach.support.AuthTestClient.Session;
import com.aicoach.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** US-A4 profile and account deletion. */
@IntegrationTest
class ProfileIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private AuthTestClient auth;

    @BeforeEach
    void setUp() {
        auth = new AuthTestClient(mvc);
    }

    private String companyId(String slug) {
        return jdbc.queryForObject("SELECT id::text FROM companies WHERE slug = ?", String.class, slug);
    }

    @Test
    void companiesArePublicSortedAndCacheable() throws Exception {
        String body = mvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=3600")))
                .andExpect(jsonPath("$[0].name").value("Adobe"))
                .andReturn().getResponse().getContentAsString();
        JsonNode list = json.readTree(body);
        assertThat(list.size()).isGreaterThanOrEqualTo(15);
        assertThat(list.findValuesAsText("slug")).contains("amazon", "google", "flipkart");
    }

    @Test
    void createsAndReplacesTheProfile() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail());
        String amazon = companyId("amazon");
        String google = companyId("google");

        mvc.perform(put("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRole\":\"SDE_2\",\"experienceYears\":2.5,\"preferredLanguage\":\"JAVA\","
                                + "\"targetCompanyIds\":[\"" + google + "\",\"" + amazon + "\",\"" + amazon + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetRole").value("SDE_2"))
                .andExpect(jsonPath("$.experienceYears").value(2.5))
                .andExpect(jsonPath("$.targetCompanies.length()").value(2))
                .andExpect(jsonPath("$.targetCompanies[0].slug").value("amazon"));

        mvc.perform(put("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRole\":\"SDE_1\",\"experienceYears\":0,\"preferredLanguage\":\"PYTHON\","
                                + "\"targetCompanyIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCompanies.length()").value(0));

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$.profile.targetRole").value("SDE_1"))
                .andExpect(jsonPath("$.profile.preferredLanguage").value("PYTHON"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_target_companies WHERE user_id = ?",
                Integer.class, session.userId())).isZero();
    }

    @Test
    void validatesProfileInput() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail());
        mvc.perform(put("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRole\":\"SDE_1\",\"experienceYears\":1.3,\"preferredLanguage\":\"PYTHON\","
                                + "\"targetCompanyIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].path").value("body.experienceYears"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a multiple of 0.5"));

        mvc.perform(put("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRole\":\"SDE_1\",\"experienceYears\":31,\"preferredLanguage\":\"PYTHON\","
                                + "\"targetCompanyIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].path").value("body.experienceYears"));

        mvc.perform(put("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRole\":\"SDE_1\",\"experienceYears\":1,\"preferredLanguage\":\"PYTHON\","
                                + "\"targetCompanyIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].path").value("body.targetCompanyIds"));

        mvc.perform(put("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRole\":\"SDE_3\",\"experienceYears\":1,\"preferredLanguage\":\"PYTHON\","
                                + "\"targetCompanyIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void deletingTheAccountErasesDataRevokesSessionsAndFreesTheEmail() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        Session session = auth.register(email);
        mvc.perform(put("/api/v1/me/profile").header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetRole\":\"SDE_1\",\"experienceYears\":1,\"preferredLanguage\":\"CPP\","
                        + "\"targetCompanyIds\":[\"" + companyId("uber") + "\"]}")).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"yes\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"DELETE\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, session.userId()))
                .startsWith("deleted+").endsWith("@deleted.invalid");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE user_id = ?", Integer.class,
                session.userId())).isZero();
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.cookie())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + AuthTestClient.PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        auth.register(email); // the address can be used again
    }
}
