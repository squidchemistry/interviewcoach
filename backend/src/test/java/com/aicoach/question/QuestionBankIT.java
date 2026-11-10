package com.aicoach.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.aicoach.question.service.EmbeddingBackfillJob;
import com.aicoach.support.AuthTestClient;
import com.aicoach.support.AuthTestClient.Session;
import com.aicoach.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/** Catalogue browsing, similarity search (pgvector) and admin content management. */
@IntegrationTest
class QuestionBankIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    EmbeddingBackfillJob embeddings;

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode call(RequestBuilder builder, int expected) throws Exception {
        var response = mvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }

    private Session admin() throws Exception {
        return new AuthTestClient(mvc).login("admin@aicoach.dev", "AdminPassw0rd");
    }

    @Test
    void candidatesBrowseWithoutSeeingAnswers() throws Exception {
        Session s = new AuthTestClient(mvc).register(AuthTestClient.uniqueEmail());
        JsonNode page = call(get("/api/v1/questions?type=DSA&pageSize=50").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        assertThat(page.get("total").asInt()).isEqualTo(30);
        String slug = page.get("items").get(0).get("slug").asText();

        JsonNode question = call(get("/api/v1/questions/" + slug).header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        String body = question.toString();
        assertThat(body).doesNotContain("referenceSolutionPython", "\"hints\"", "testCases");
        assertThat(body).contains("starterCode");

        JsonNode filtered = call(get("/api/v1/questions?type=BEHAVIOURAL&q=disagreement").header(HttpHeaders.AUTHORIZATION,
                s.bearer()), 200);
        assertThat(filtered.get("items").size()).isGreaterThan(0);
        assertThat(call(get("/api/v1/tags").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200).size()).isGreaterThan(10);
        assertThat(call(get("/api/v1/packs").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200).size()).isGreaterThan(0);

        embeddings.embedPending();
        JsonNode similar = call(get("/api/v1/questions/" + slug + "/similar?k=3").header(HttpHeaders.AUTHORIZATION,
                s.bearer()), 200);
        assertThat(similar.size()).isEqualTo(3);
        assertThat(similar.findValuesAsText("slug")).doesNotContain(slug);
        call(get("/api/v1/questions/does-not-exist").header(HttpHeaders.AUTHORIZATION, s.bearer()), 404);
    }

    @Test
    void adminsManageQuestionsRubricsAndSeeAiUsage() throws Exception {
        Session admin = admin();
        ObjectNode detail = json.createObjectNode();
        detail.put("competency", "OWNERSHIP");
        detail.put("whatGoodLooksLike", "A specific story with measurable results.");
        detail.set("followUpProbes", json.valueToTree(List.of("What would you do differently?")));
        detail.set("expectedPoints", json.valueToTree(List.of("specific situation", "measurable result")));
        Map<String, Object> request = new java.util.HashMap<>(Map.of("type", "BEHAVIOURAL", "slug", "admin-created-story",
                "title", "Tell me about a time you fixed a production issue", "bodyMd", "Describe it.",
                "difficulty", "MEDIUM", "status", "DRAFT", "tags", List.of("ownership"), "companies", List.of(),
                "detail", detail));

        JsonNode created = call(post("/api/v1/admin/questions").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)), 201);
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");

        // invalid detail for the type is rejected at the boundary
        Map<String, Object> bad = new java.util.HashMap<>(request);
        bad.put("slug", "admin-bad-detail");
        bad.put("detail", json.createObjectNode().put("nope", true));
        call(post("/api/v1/admin/questions").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)), 400);

        request.put("title", "Tell me about the Kestrel outage you owned");
        request.put("expectedVersion", created.get("version").asLong());
        JsonNode updated = call(put("/api/v1/admin/questions/" + id).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)), 200);
        assertThat(updated.get("title").asText()).contains("Kestrel");
        call(put("/api/v1/admin/questions/" + id).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)), 409);

        JsonNode published = call(post("/api/v1/admin/questions/" + id + "/status").header(HttpHeaders.AUTHORIZATION,
                admin.bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"), 200);
        assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(call(get("/api/v1/admin/questions?status=PUBLISHED&q=kestrel").header(HttpHeaders.AUTHORIZATION,
                admin.bearer()), 200).get("total").asInt()).isEqualTo(1);
        call(get("/api/v1/admin/questions/" + id).header(HttpHeaders.AUTHORIZATION, admin.bearer()), 200);

        JsonNode reindex = call(post("/api/v1/admin/embeddings/reindex").header(HttpHeaders.AUTHORIZATION, admin.bearer()), 200);
        assertThat(reindex.get("model").asText()).isNotBlank();

        JsonNode rubrics = call(get("/api/v1/admin/rubrics").header(HttpHeaders.AUTHORIZATION, admin.bearer()), 200);
        JsonNode dsa = null;
        for (JsonNode r : rubrics) {
            if ("DSA".equals(r.get("key").asText()) && r.get("active").asBoolean()) {
                dsa = r;
            }
        }
        assertThat(dsa).isNotNull();
        JsonNode newVersion = call(post("/api/v1/admin/rubrics").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("key", "DSA",
                        "definition", dsa.get("definition"), "changelog", "Copy for testing"))), 201);
        assertThat(newVersion.get("version").asInt()).isEqualTo(dsa.get("version").asInt() + 1);
        assertThat(newVersion.get("active").asBoolean()).isFalse();
        JsonNode activated = call(post("/api/v1/admin/rubrics/" + newVersion.get("id").asText() + "/activate")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()), 200);
        assertThat(activated.get("active").asBoolean()).isTrue();
        // restore the original so other tests see the seeded rubric
        call(post("/api/v1/admin/rubrics/" + dsa.get("id").asText() + "/activate").header(HttpHeaders.AUTHORIZATION,
                admin.bearer()), 200);

        ObjectNode broken = dsa.get("definition").deepCopy();
        ((ObjectNode) broken.get("competencies").get(0)).put("weight", 0.95);
        call(post("/api/v1/admin/rubrics").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("key", "DSA",
                        "definition", broken))), 400);

        call(get("/api/v1/admin/ai-usage?groupBy=purpose&days=7").header(HttpHeaders.AUTHORIZATION, admin.bearer()), 200);
        Session user = new AuthTestClient(mvc).register(AuthTestClient.uniqueEmail());
        call(get("/api/v1/admin/rubrics").header(HttpHeaders.AUTHORIZATION, user.bearer()), 403);
    }
}
