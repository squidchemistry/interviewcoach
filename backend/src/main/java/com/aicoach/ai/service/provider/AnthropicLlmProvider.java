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

/** Adapter for Anthropic's Messages API (Claude). */
public class AnthropicLlmProvider implements LlmProvider {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    private final HttpLlmSupport http;
    private final ObjectMapper json;
    private final URI endpoint;
    private final Map<String, String> headers;

    public AnthropicLlmProvider(String apiKey, String baseUrl, Duration timeout, ObjectMapper json) {
        this.http = new HttpLlmSupport(timeout);
        this.json = json;
        String base = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.replaceAll("/+$", "");
        this.endpoint = URI.create(base + "/v1/messages");
        this.headers = Map.of("x-api-key", apiKey, "anthropic-version", API_VERSION);
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        JsonNode response = read(http.post(endpoint, headers, body(request, false)));
        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        JsonNode usage = response.path("usage");
        return new LlmResult(text.toString(), usage.path("input_tokens").asInt(), usage.path("output_tokens").asInt(),
                response.path("model").asText(request.model()), false);
    }

    @Override
    public LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
        StringBuilder text = new StringBuilder();
        AtomicInteger input = new AtomicInteger();
        AtomicInteger output = new AtomicInteger();
        http.postStreaming(endpoint, headers, body(request, true), data -> {
            JsonNode event = read(data);
            switch (event.path("type").asText()) {
                case "message_start" -> input.set(event.path("message").path("usage").path("input_tokens").asInt());
                case "content_block_delta" -> {
                    String delta = event.path("delta").path("text").asText("");
                    if (!delta.isEmpty()) {
                        text.append(delta);
                        onDelta.accept(delta);
                    }
                }
                case "message_delta" -> output.set(event.path("usage").path("output_tokens").asInt());
                case "error" -> throw new AiException.Transient("Anthropic stream error: "
                        + event.path("error").path("type").asText(), null);
                default -> {
                    // ping, content_block_start/stop, message_stop: nothing to do
                }
            }
        });
        return new LlmResult(text.toString(), input.get(), output.get(), request.model(), false);
    }

    private String body(LlmRequest request, boolean stream) {
        ObjectNode body = json.createObjectNode();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        body.put("temperature", request.temperature());
        body.put("system", request.system());
        body.put("stream", stream);
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage message : request.messages()) {
            messages.addObject().put("role", message.role()).put("content", message.content());
        }
        return body.toString();
    }

    private JsonNode read(String payload) {
        try {
            return json.readTree(payload);
        } catch (Exception e) {
            throw new AiException.Transient("Unreadable response from Anthropic", e);
        }
    }
}
