package com.aicoach.ai.service.provider;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiTypes.ChatMessage;
import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Adapter for OpenAI's Chat Completions API and compatible endpoints. Point {@code baseUrl} at
 * {@code https://generativelanguage.googleapis.com/v1beta/openai} to use Gemini through the same adapter.
 */
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final HttpLlmSupport http;
    private final ObjectMapper json;
    private final URI endpoint;
    private final Map<String, String> headers;

    public OpenAiCompatibleLlmProvider(String apiKey, String baseUrl, Duration timeout, ObjectMapper json) {
        this.http = new HttpLlmSupport(timeout);
        this.json = json;
        String base = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.replaceAll("/+$", "");
        this.endpoint = URI.create(base + "/chat/completions");
        this.headers = Map.of("Authorization", "Bearer " + apiKey);
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        JsonNode response = read(http.post(endpoint, headers, body(request, false)));
        String text = response.path("choices").path(0).path("message").path("content").asText("");
        JsonNode usage = response.path("usage");
        return new LlmResult(text, usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(),
                response.path("model").asText(request.model()), false);
    }

    @Override
    public LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
        StringBuilder text = new StringBuilder();
        AtomicInteger input = new AtomicInteger();
        AtomicInteger output = new AtomicInteger();
        http.postStreaming(endpoint, headers, body(request, true), data -> {
            JsonNode chunk = read(data);
            String delta = chunk.path("choices").path(0).path("delta").path("content").asText("");
            if (!delta.isEmpty()) {
                text.append(delta);
                onDelta.accept(delta);
            }
            JsonNode usage = chunk.path("usage");
            if (usage.isObject()) {
                input.set(usage.path("prompt_tokens").asInt());
                output.set(usage.path("completion_tokens").asInt());
            }
        });
        return new LlmResult(text.toString(), input.get(), output.get(), request.model(), false);
    }

    private String body(LlmRequest request, boolean stream) {
        ObjectNode body = json.createObjectNode();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        body.put("temperature", request.temperature());
        body.put("stream", stream);
        if (stream) {
            body.putObject("stream_options").put("include_usage", true);
        }
        if (request.json()) {
            body.putObject("response_format").put("type", "json_object");
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.system());
        for (ChatMessage message : request.messages()) {
            messages.addObject().put("role", message.role()).put("content", message.content());
        }
        return body.toString();
    }

    private JsonNode read(String payload) {
        try {
            return json.readTree(payload);
        } catch (Exception e) {
            throw new AiException.Transient("Unreadable response from the AI provider", e);
        }
    }
}
