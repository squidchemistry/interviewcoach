package com.aicoach.ai.service.embedding;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiTypes.EmbeddingProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** OpenAI embeddings (works with compatible endpoints), requested at the fixed 1024 dimensions of the vector column. */
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String apiKey;
    private final URI endpoint;
    private final String model;

    public OpenAiEmbeddingProvider(String apiKey, String baseUrl, String model, ObjectMapper json) {
        this.apiKey = apiKey;
        this.model = model;
        this.json = json;
        String base = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.replaceAll("/+$", "");
        this.endpoint = URI.create(base + "/embeddings");
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public float[] embed(String text) {
        try {
            String body = json.createObjectNode().put("model", model).put("input", text).put("dimensions", DIMENSIONS)
                    .toString();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new AiException.Transient("Embedding request failed with HTTP " + response.statusCode(), null);
            }
            JsonNode vector = json.readTree(response.body()).path("data").path(0).path("embedding");
            if (vector.size() != DIMENSIONS) {
                throw AiException.unavailable("Embedding has " + vector.size() + " dimensions, expected " + DIMENSIONS, null);
            }
            float[] result = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                result[i] = (float) vector.get(i).asDouble();
            }
            return result;
        } catch (IOException e) {
            throw new AiException.Transient("Embedding request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException.Transient("Interrupted", e);
        }
    }
}
