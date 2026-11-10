package com.aicoach.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicoach.support.AuthTestClient;
import com.aicoach.support.AuthTestClient.Session;
import com.aicoach.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end interview flows against real Postgres + Redis, with AI_PROVIDER=mock and CODE_EXECUTOR=mock. */
@IntegrationTest
class InterviewFlowIT {

    private static final String LONG_ANSWER = "In my last internship our checkout service kept timing out during sales. "
            + "I was responsible for the payments integration, so I owned the investigation. I added tracing, found "
            + "that we retried the payment gateway synchronously three times, and moved the retries to a queue with "
            + "idempotency keys. As a result p99 latency dropped from four seconds to six hundred milliseconds and "
            + "failed checkouts fell by about forty percent. I learned to measure before optimising.";

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

    // ------------------------------------------------------------------ helpers

    private JsonNode call(org.springframework.test.web.servlet.RequestBuilder builder, int expected) throws Exception {
        var response = mvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        String body = response.getContentAsString();
        return body.isBlank() ? json.nullNode() : json.readTree(body);
    }

    private JsonNode create(Session s, Map<String, Object> body, int expected) throws Exception {
        return call(post("/api/v1/sessions").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), expected);
    }

    private JsonNode detail(Session s, String id) throws Exception {
        return call(get("/api/v1/sessions/" + id).header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
    }

    /** Polls the session until the candidate may act (ANSWER, not processing) or the session left IN_PROGRESS. */
    private JsonNode awaitTurn(Session s, String id) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode d = detail(s, id);
            JsonNode session = d.get("session");
            String status = session.get("status").asText();
            boolean processing = session.get("processing").asBoolean();
            if (!"IN_PROGRESS".equals(status) && !"CREATED".equals(status)) {
                return d;
            }
            if ("ANSWER".equals(session.path("stage").asText()) && !processing) {
                return d;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("Session " + id + " never became ready: " + detail(s, id));
    }

    private JsonNode awaitStatus(Session s, String id, String wanted) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode d = detail(s, id);
            if (wanted.equals(d.get("session").get("status").asText())) {
                return d;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Session " + id + " never reached " + wanted + ": " + detail(s, id));
    }

    private JsonNode answer(Session s, String id, UUID clientMessageId, String content, int expected) throws Exception {
        return call(post("/api/v1/sessions/" + id + "/answers").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clientMessageId", clientMessageId, "content", content))), expected);
    }

    private void makePro(Session s) {
        jdbc.update("UPDATE users SET plan = 'PRO' WHERE id = ?", s.userId());
    }

    // ------------------------------------------------------------------ tests

    @Test
    void behaviouralInterviewRunsToAReportAndCanBeSharedPublicly() throws Exception {
        Session s = auth.register(AuthTestClient.uniqueEmail(), AuthTestClient.PASSWORD, "Priya Sharma");
        JsonNode created = create(s, Map.of("mode", "BEHAVIOURAL", "questionCount", 3), 201);
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("CREATED");

        call(post("/api/v1/sessions/" + id + "/start").header(HttpHeaders.AUTHORIZATION, s.bearer()), 202);
        for (int i = 0; i < 12; i++) {
            JsonNode d = awaitTurn(s, id);
            if (!"IN_PROGRESS".equals(d.get("session").get("status").asText())) {
                break;
            }
            answer(s, id, UUID.randomUUID(), LONG_ANSWER, 202);
        }
        JsonNode done = awaitStatus(s, id, "COMPLETED");
        assertThat(done.get("questions").size()).isEqualTo(3);
        JsonNode transcript = done.get("transcript");
        assertThat(transcript.findValuesAsText("role")).contains("INTERVIEWER", "CANDIDATE");
        assertThat(transcript.get(0).get("kind").asText()).isEqualTo("QUESTION");

        JsonNode report = call(get("/api/v1/sessions/" + id + "/report").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        JsonNode evaluation = report.get("evaluation");
        assertThat(evaluation.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(evaluation.get("overallScore").asDouble()).isBetween(0.0, 100.0);
        assertThat(evaluation.get("competencies").size()).isGreaterThanOrEqualTo(2);
        assertThat(evaluation.get("nextSteps").size()).isEqualTo(3);
        assertThat(evaluation.get("perQuestion").size()).isEqualTo(3);
        assertThat(evaluation.get("rubric").get("key").asText()).isEqualTo("BEHAVIOURAL");
        assertThat(evaluation.get("evaluator").get("provider").asText()).isEqualToIgnoringCase("mock");
        // every evidence quote must be verbatim candidate text
        for (JsonNode c : evaluation.get("competencies")) {
            for (JsonNode e : c.get("evidence")) {
                assertThat(LONG_ANSWER.toLowerCase()).contains(e.get("quote").asText().toLowerCase().replaceAll("[.…]+$", "").trim());
            }
        }

        JsonNode progress = call(get("/api/v1/me/progress").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        assertThat(progress.get("totals").get("completed").asInt()).isEqualTo(1);
        assertThat(progress.get("scores").size()).isEqualTo(1);
        assertThat(progress.get("trends").size()).isGreaterThan(0);
        JsonNode revision = call(get("/api/v1/me/revision-queue").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        assertThat(revision.get("items").size()).isGreaterThan(0);

        // the SSE stream of a finished session replays its events and closes
        MvcResult async = mvc.perform(get("/api/v1/sessions/" + id + "/stream").header(HttpHeaders.AUTHORIZATION, s.bearer()))
                .andExpect(request().asyncStarted()).andReturn();
        async.getAsyncResult(10_000);
        String events = mvc.perform(asyncDispatch(async)).andReturn().getResponse().getContentAsString();
        assertThat(events).contains("event:turn.started", "event:token", "event:turn.completed", "event:state.changed");

        // sharing: public, anonymised, revocable
        JsonNode link = call(post("/api/v1/sessions/" + id + "/share").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expiresInDays\":7}"), 201);
        String url = link.get("url").asText();
        String token = url.substring(url.lastIndexOf('/') + 1);
        mvc.perform(get("/api/v1/public/reports/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(jsonPath("$.candidateFirstName").value("Priya"))
                .andExpect(jsonPath("$.transcript").doesNotExist());
        call(delete("/api/v1/sessions/" + id + "/share").header(HttpHeaders.AUTHORIZATION, s.bearer()), 204);
        mvc.perform(get("/api/v1/public/reports/" + token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/reports/not-a-real-token")).andExpect(status().isNotFound());
    }

    @Test
    void answersAreIdempotentAndOtherUsersCannotSeeTheSession() throws Exception {
        Session s = auth.register(AuthTestClient.uniqueEmail());
        String id = create(s, Map.of("mode", "BEHAVIOURAL", "questionCount", 3), 201).get("id").asText();
        call(post("/api/v1/sessions/" + id + "/start").header(HttpHeaders.AUTHORIZATION, s.bearer()), 202);
        awaitTurn(s, id);

        UUID clientMessageId = UUID.randomUUID();
        answer(s, id, clientMessageId, LONG_ANSWER, 202);
        answer(s, id, clientMessageId, LONG_ANSWER, 202); // replay: accepted, no second turn
        awaitTurn(s, id);
        long answers = jdbc.queryForObject("SELECT count(*) FROM turns WHERE session_id = ?::uuid AND kind = 'ANSWER'",
                Long.class, id);
        assertThat(answers).isEqualTo(1);

        Session other = auth.register(AuthTestClient.uniqueEmail());
        call(get("/api/v1/sessions/" + id).header(HttpHeaders.AUTHORIZATION, other.bearer()), 404);
        answer(other, id, UUID.randomUUID(), "hello", 404);

        // starting twice is an invalid transition; ending early evaluates the answered question
        call(post("/api/v1/sessions/" + id + "/start").header(HttpHeaders.AUTHORIZATION, s.bearer()), 409);
        JsonNode ended = call(post("/api/v1/sessions/" + id + "/end").header(HttpHeaders.AUTHORIZATION, s.bearer()), 202);
        assertThat(ended.get("status").asText()).isEqualTo("EVALUATING");
        awaitStatus(s, id, "COMPLETED");
        assertThat(detail(s, id).get("session").get("endReason").asText()).isEqualTo("ENDED_BY_USER");
    }

    @Test
    void abandonedWithoutAnswersHasNoReport() throws Exception {
        Session s = auth.register(AuthTestClient.uniqueEmail());
        String id = create(s, Map.of("mode", "LLD", "questionCount", 1), 201).get("id").asText();
        call(post("/api/v1/sessions/" + id + "/start").header(HttpHeaders.AUTHORIZATION, s.bearer()), 202);
        awaitTurn(s, id);
        JsonNode ended = call(post("/api/v1/sessions/" + id + "/end").header(HttpHeaders.AUTHORIZATION, s.bearer()), 202);
        assertThat(ended.get("status").asText()).isEqualTo("ABANDONED");
        call(get("/api/v1/sessions/" + id + "/report").header(HttpHeaders.AUTHORIZATION, s.bearer()), 409);
    }

    @Test
    void codingRoundSupportsOrderedHintsAndLabelledMockExecution() throws Exception {
        Session s = auth.register(AuthTestClient.uniqueEmail());
        String id = create(s, Map.of("mode", "DSA", "questionCount", 1), 201).get("id").asText();
        call(post("/api/v1/sessions/" + id + "/start").header(HttpHeaders.AUTHORIZATION, s.bearer()), 202);
        JsonNode d = awaitTurn(s, id);
        JsonNode context = d.get("questions").get(0).get("context");
        assertThat(context.toString()).contains("starterCode");
        assertThat(d.get("codeExecutionEnabled").asBoolean()).isFalse();

        JsonNode outOfOrder = call(post("/api/v1/sessions/" + id + "/hints").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":2}"), 409);
        assertThat(outOfOrder.get("code").asText()).isEqualTo("HINT_ORDER");
        call(post("/api/v1/sessions/" + id + "/hints").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":1}"), 202);
        d = awaitTurn(s, id);
        assertThat(d.get("questions").get(0).get("hintsUsed").asInt()).isEqualTo(1);
        assertThat(d.get("transcript").findValuesAsText("kind")).contains("HINT");

        String code = "{\"language\":\"PYTHON\",\"source\":\"def solve():\\n    return 1\\n\"}";
        JsonNode run = call(post("/api/v1/sessions/" + id + "/code/run").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(code), 200);
        assertThat(run.get("executed").asBoolean()).isFalse();
        assertThat(run.get("verdict").asText()).isEqualTo("NOT_EXECUTED");
        call(post("/api/v1/sessions/" + id + "/code/submit").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(code), 200);
        d = detail(s, id);
        assertThat(d.get("transcript").findValuesAsText("kind")).contains("CODE_RUN");

        for (int i = 0; i < 6; i++) {
            if (!"IN_PROGRESS".equals(awaitTurn(s, id).get("session").get("status").asText())) {
                break;
            }
            answer(s, id, UUID.randomUUID(), "I push every opening bracket on a stack and pop on each closing bracket, "
                    + "checking that the kinds match; at the end the stack must be empty. One pass: O(n) time, O(n) space.", 202);
        }
        awaitStatus(s, id, "COMPLETED");
        JsonNode report = call(get("/api/v1/sessions/" + id + "/report").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        assertThat(report.get("evaluation").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(report.toString()).contains("hint");

        // hints are coding-only
        String behavioural = create(s, Map.of("mode", "BEHAVIOURAL"), 201).get("id").asText();
        call(post("/api/v1/sessions/" + behavioural + "/start").header(HttpHeaders.AUTHORIZATION, s.bearer()), 202);
        awaitTurn(s, behavioural);
        call(post("/api/v1/sessions/" + behavioural + "/hints").header(HttpHeaders.AUTHORIZATION, s.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":1}"), 422);
    }

    @Test
    void freePlanQuotaAndProFeaturesAreEnforced() throws Exception {
        Session s = auth.register(AuthTestClient.uniqueEmail());
        JsonNode voice = create(s, Map.of("mode", "BEHAVIOURAL", "voice", true), 402);
        assertThat(voice.get("code").asText()).isEqualTo("PRO_REQUIRED");
        create(s, Map.of("mode", "DSA", "questionCount", 9), 400);
        create(s, Map.of("mode", "RESUME_DEEP_DIVE"), 422);
        for (int i = 0; i < 3; i++) {
            create(s, Map.of("mode", "LLD", "questionCount", 1), 201);
        }
        JsonNode quota = create(s, Map.of("mode", "LLD", "questionCount", 1), 402);
        assertThat(quota.get("code").asText()).isEqualTo("QUOTA_EXCEEDED");
        JsonNode usage = call(get("/api/v1/me/usage").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        assertThat(usage.get("interviewsStarted").asInt()).isEqualTo(3);
        assertThat(usage.get("interviewLimit").asInt()).isEqualTo(3);

        makePro(s);
        create(s, Map.of("mode", "BEHAVIOURAL", "voice", true), 201);
        JsonNode proUsage = call(get("/api/v1/me/usage").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        assertThat(proUsage.has("interviewLimit")).isFalse();

        JsonNode list = call(get("/api/v1/sessions?mode=LLD").header(HttpHeaders.AUTHORIZATION, s.bearer()), 200);
        assertThat(list.get("total").asInt()).isEqualTo(3);
    }
}
