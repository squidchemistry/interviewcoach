package com.aicoach.execution.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicoach.common.error.AppException;
import com.aicoach.execution.service.CodeExecutor.RawResult;
import com.aicoach.execution.service.CodeExecutor.RawStatus;
import com.aicoach.user.domain.CodeLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Judge0 batch submission and polling against WireMock (Judge0 itself cannot run on cgroup v2 hosts). */
class Judge0CodeExecutorTest {

    static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());

    @BeforeAll
    static void start() {
        SERVER.start();
    }

    @AfterAll
    static void stop() {
        SERVER.stop();
    }

    @BeforeEach
    void reset() {
        SERVER.resetAll();
    }

    static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    Judge0CodeExecutor executor(Duration timeout) {
        return new Judge0CodeExecutor("http://localhost:" + SERVER.port() + "/", Map.of("X-Auth-Token", "t0k"), timeout,
                new ObjectMapper(), 5);
    }

    @Test
    void submitsABatchPollsUntilDoneAndMapsStatuses() {
        SERVER.stubFor(post(urlPathEqualTo("/submissions/batch"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"token\":\"a\"},{\"token\":\"b\"},{\"token\":\"c\"}]")));
        SERVER.stubFor(get(urlPathEqualTo("/submissions/batch")).inScenario("poll").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(
                        "{\"submissions\":[{\"status\":{\"id\":2}},{\"status\":{\"id\":1}},{\"status\":{\"id\":1}}]}"))
                .willSetStateTo("done"));
        SERVER.stubFor(get(urlPathEqualTo("/submissions/batch")).inScenario("poll").whenScenarioStateIs("done")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"submissions\":["
                        + "{\"status\":{\"id\":3},\"stdout\":\"" + b64("[0,1]\n") + "\",\"time\":\"0.012\",\"memory\":3100},"
                        + "{\"status\":{\"id\":5},\"stdout\":null,\"time\":\"2.0\"},"
                        + "{\"status\":{\"id\":11},\"stderr\":\"" + b64("java.lang.OutOfMemoryError: Java heap space") + "\"}]}")));

        List<RawResult> results = executor(Duration.ofSeconds(5)).run(CodeLanguage.PYTHON, "print(1)", List.of("1\n", "2\n", "3\n"));

        assertThat(results).extracting(RawResult::status)
                .containsExactly(RawStatus.OK, RawStatus.TIME_LIMIT_EXCEEDED, RawStatus.MEMORY_LIMIT_EXCEEDED);
        assertThat(results.getFirst().stdout()).isEqualTo("[0,1]\n");
        assertThat(results.getFirst().timeMs()).isEqualTo(12);
        SERVER.verify(postRequestedFor(urlPathEqualTo("/submissions/batch")).withHeader("X-Auth-Token", equalTo("t0k")));
    }

    @Test
    void mapsCompilationErrorsAndUnknownStatuses() {
        RawResult compile = Judge0CodeExecutor.map(new ObjectMapper().createObjectNode()
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("status", new ObjectMapper().createObjectNode().put("id", 6))
                .put("compile_output", b64("Main.java:3: error")));
        assertThat(compile.status()).isEqualTo(RawStatus.COMPILATION_ERROR);
        assertThat(compile.compileOutput()).contains("error");
        RawResult internal = Judge0CodeExecutor.map(new ObjectMapper().createObjectNode()
                .set("status", new ObjectMapper().createObjectNode().put("id", 13)));
        assertThat(internal.status()).isEqualTo(RawStatus.INTERNAL_ERROR);
    }

    @Test
    void reportsAnUnavailableSandbox() {
        SERVER.stubFor(post(urlPathEqualTo("/submissions/batch")).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> executor(Duration.ofSeconds(2)).run(CodeLanguage.JAVA, "x", List.of("")))
                .isInstanceOf(AppException.class).hasMessageContaining("503");

        SERVER.stubFor(post(urlPathEqualTo("/submissions/batch"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[{\"error\":\"bad\"}]")));
        assertThatThrownBy(() -> executor(Duration.ofSeconds(2)).run(CodeLanguage.JAVA, "x", List.of("")))
                .hasMessageContaining("rejected");

        SERVER.stubFor(post(urlPathEqualTo("/submissions/batch"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[{\"token\":\"a\"}]")));
        SERVER.stubFor(get(urlPathEqualTo("/submissions/batch")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"submissions\":[{\"status\":{\"id\":1}}]}")));
        assertThatThrownBy(() -> executor(Duration.ofMillis(200)).run(CodeLanguage.JAVA, "x", List.of("")))
                .hasMessageContaining("timed out");
    }
}
