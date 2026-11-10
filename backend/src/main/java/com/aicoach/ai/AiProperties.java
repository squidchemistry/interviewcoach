package com.aicoach.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * LLM and embedding configuration ({@code app.ai.*}). {@code provider=mock} runs the whole product with zero paid
 * keys: a deterministic, clearly labelled simulator that produces realistic, schema-valid output.
 */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @NotBlank @Pattern(regexp = "mock|anthropic|openai") String provider,
        String apiKey,
        String baseUrl,
        @NotBlank String modelFast,
        @NotBlank String modelSmart,
        @NotNull Duration timeout,
        @Min(0) @Max(5) int maxRetries,
        @NotNull Duration retryBackoff,
        @NotNull Duration cacheTtl,
        @Positive int sessionTokenBudget,
        @Positive int dailyTokenBudget,
        @Min(0) @Max(200) int mockStreamDelayMs,
        @NotNull @Valid Embedding embedding) {

    public boolean isMock() {
        return "mock".equals(provider);
    }

    public record Embedding(
            @NotBlank @Pattern(regexp = "mock|openai") String provider,
            String apiKey,
            String baseUrl,
            @NotBlank String model) {
    }
}
