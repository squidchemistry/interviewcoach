package com.aicoach.ai.service.provider;

import com.aicoach.ai.AiException;
import com.aicoach.common.error.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Shared HTTP plumbing for vendor adapters: timeouts, error classification and SSE line streaming. */
final class HttpLlmSupport {

    private final HttpClient client;
    private final Duration timeout;

    HttpLlmSupport(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    String post(URI uri, Map<String, String> headers, String body) {
        HttpResponse<String> response = send(uri, headers, body, HttpResponse.BodyHandlers.ofString());
        check(response.statusCode(), response.body());
        return response.body();
    }

    /** POSTs and feeds each {@code data:} payload of the server-sent-event response to {@code onData}. */
    void postStreaming(URI uri, Map<String, String> headers, String body, Consumer<String> onData) {
        HttpResponse<Stream<String>> response = send(uri, headers, body, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() >= 300) {
            String errorBody = String.join("\n", response.body().limit(50).toList());
            check(response.statusCode(), errorBody);
        }
        try (Stream<String> lines = response.body()) {
            lines.filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim())
                    .filter(data -> !data.isEmpty() && !"[DONE]".equals(data))
                    .forEach(onData);
        } catch (java.io.UncheckedIOException e) {
            throw new AiException.Transient("Stream interrupted", e);
        }
    }

    private <T> HttpResponse<T> send(URI uri, Map<String, String> headers, String body,
            HttpResponse.BodyHandler<T> handler) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        try {
            return client.send(request.build(), handler);
        } catch (IOException e) {
            throw new AiException.Transient("AI provider request failed: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException.Transient("Interrupted while calling the AI provider", e);
        }
    }

    private static void check(int status, String body) {
        if (status == 429 || status >= 500) {
            throw new AiException.Transient("AI provider returned HTTP " + status, null);
        }
        if (status >= 300) {
            String snippet = body == null ? "" : body.substring(0, Math.min(300, body.length()));
            throw new AiException(ErrorCode.AI_UNAVAILABLE, "AI provider rejected the request (HTTP " + status + ")",
                    "ERROR", new IllegalStateException(snippet));
        }
    }
}
