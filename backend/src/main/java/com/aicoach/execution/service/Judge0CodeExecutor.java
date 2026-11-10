package com.aicoach.execution.service;

import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.user.domain.CodeLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Judge0 CE (self-hosted, or the hosted RapidAPI edition with the same API). Submits a batch, polls
 * with backoff until every submission finishes, and maps Judge0 status ids to our statuses. Limits: 2 s CPU,
 * 5 s wall, 256 MB, no network.
 */
public class Judge0CodeExecutor implements CodeExecutor {

    /** Judge0 CE 1.13 language ids. */
    static final Map<CodeLanguage, Integer> LANGUAGE_IDS = Map.of(
            CodeLanguage.PYTHON, 71, CodeLanguage.JAVA, 62, CodeLanguage.CPP, 54, CodeLanguage.JAVASCRIPT, 63);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final Map<String, String> authHeaders;
    private final Duration timeout;
    private final long initialPollMs;

    public Judge0CodeExecutor(String baseUrl, Map<String, String> authHeaders, Duration timeout, ObjectMapper json,
            long initialPollMs) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.authHeaders = authHeaders;
        this.timeout = timeout;
        this.json = json;
        this.initialPollMs = initialPollMs;
    }

    @Override
    public String name() {
        return "judge0";
    }

    @Override
    public boolean executesCode() {
        return true;
    }

    @Override
    public List<RawResult> run(CodeLanguage language, String program, List<String> stdins) {
        ObjectNode body = json.createObjectNode();
        ArrayNode submissions = body.putArray("submissions");
        for (String stdin : stdins) {
            submissions.addObject()
                    .put("language_id", LANGUAGE_IDS.get(language))
                    .put("source_code", b64(program))
                    .put("stdin", b64(stdin))
                    .put("cpu_time_limit", 2)
                    .put("wall_time_limit", 5)
                    .put("memory_limit", 262_144)
                    .put("enable_network", false);
        }
        JsonNode created = send(HttpRequest.newBuilder(URI.create(baseUrl + "/submissions/batch?base64_encoded=true"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
        List<String> tokens = new ArrayList<>();
        for (JsonNode node : created) {
            if (!node.hasNonNull("token")) {
                throw new AppException(ErrorCode.CODE_EXECUTOR_UNAVAILABLE, "Judge0 rejected the submission: " + node);
            }
            tokens.add(node.get("token").asText());
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        long delay = initialPollMs;
        while (true) {
            JsonNode batch = send(HttpRequest.newBuilder(URI.create(baseUrl + "/submissions/batch?tokens="
                    + String.join(",", tokens)
                    + "&base64_encoded=true&fields=token,stdout,stderr,compile_output,status,time,memory")).GET());
            JsonNode items = batch.path("submissions");
            boolean done = true;
            for (JsonNode item : items) {
                if (item.path("status").path("id").asInt(1) <= 2) {
                    done = false;
                    break;
                }
            }
            if (done) {
                List<RawResult> results = new ArrayList<>();
                items.forEach(item -> results.add(map(item)));
                return results;
            }
            if (System.nanoTime() > deadline) {
                throw new AppException(ErrorCode.CODE_EXECUTOR_UNAVAILABLE, "Code execution timed out in the sandbox queue");
            }
            sleep(delay);
            delay = Math.min(1_000, delay * 2);
        }
    }

    static RawResult map(JsonNode item) {
        int status = item.path("status").path("id").asInt();
        RawStatus mapped = switch (status) {
            case 3, 4 -> RawStatus.OK; // 4 = Judge0 "wrong answer"; we compare outputs ourselves
            case 5 -> RawStatus.TIME_LIMIT_EXCEEDED;
            case 6 -> RawStatus.COMPILATION_ERROR;
            case 7, 8, 9, 10, 11, 12 -> RawStatus.RUNTIME_ERROR;
            default -> RawStatus.INTERNAL_ERROR;
        };
        String stderr = decode(item.path("stderr"));
        if (mapped == RawStatus.RUNTIME_ERROR && stderr.toLowerCase().contains("memory")) {
            mapped = RawStatus.MEMORY_LIMIT_EXCEEDED;
        }
        Integer timeMs = item.hasNonNull("time") ? (int) Math.round(item.get("time").asDouble() * 1000) : null;
        Integer memory = item.hasNonNull("memory") ? item.get("memory").asInt() : null;
        return new RawResult(mapped, decode(item.path("stdout")), stderr, decode(item.path("compile_output")), timeMs, memory);
    }

    private JsonNode send(HttpRequest.Builder builder) {
        authHeaders.forEach(builder::header);
        try {
            HttpResponse<String> response = client.send(builder.timeout(Duration.ofSeconds(15)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new AppException(ErrorCode.CODE_EXECUTOR_UNAVAILABLE,
                        "The code sandbox returned HTTP " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new AppException(ErrorCode.CODE_EXECUTOR_UNAVAILABLE, "The code sandbox is unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.CODE_EXECUTOR_UNAVAILABLE, "Interrupted while running code", e);
        }
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        return new String(Base64.getMimeDecoder().decode(node.asText()), StandardCharsets.UTF_8);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.CODE_EXECUTOR_UNAVAILABLE, "Interrupted while running code", e);
        }
    }
}
