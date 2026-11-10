package com.aicoach.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Contracts of the AI layer (kept together: they are small and always used together). */
public final class AiTypes {

    private AiTypes() {
    }

    public enum AiPurpose {
        INTERVIEWER_TURN, TURN_ASSESSMENT, HINT, RESUME_EXTRACTION, QUESTION_GENERATION, EVALUATION, EVALUATION_REPAIR,
        EMBEDDING
    }

    /** fast → interviewer turns (latency), smart → evaluation and extraction (quality). */
    public enum ModelTier {
        FAST, SMART
    }

    /** Who/what an AI call is for: used for budgets, metering and deterministic mock output. */
    public record CallContext(UUID userId, UUID sessionId, AiPurpose purpose, long seed) {
        public static CallContext system(AiPurpose purpose) {
            return new CallContext(null, null, purpose, 0L);
        }
    }

    public record ChatMessage(String role, String content) {
        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }
    }

    /**
     * A fully rendered request. {@code variables} carries the template inputs: real providers ignore it; the mock
     * provider uses it to produce realistic output without parsing prose.
     */
    public record LlmRequest(
            String promptId,
            String promptVersion,
            ModelTier tier,
            String model,
            String system,
            List<ChatMessage> messages,
            double temperature,
            int maxTokens,
            boolean json,
            Map<String, Object> variables,
            CallContext context) {
    }

    public record LlmResult(String text, int inputTokens, int outputTokens, String model, boolean cached) {
        public LlmResult asCached() {
            return new LlmResult(text, inputTokens, outputTokens, model, true);
        }
    }

    /** Strategy: one implementation per vendor (and the mock); cross-cutting concerns are decorators. */
    public interface LlmProvider {
        String name();

        LlmResult complete(LlmRequest request);

        /** Streams text deltas to {@code onDelta} and returns the complete result. */
        default LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
            LlmResult result = complete(request);
            onDelta.accept(result.text());
            return result;
        }
    }

    /** Strategy for text embeddings (fixed 1024 dimensions so providers are interchangeable). */
    public interface EmbeddingProvider {
        int DIMENSIONS = 1024;

        String model();

        float[] embed(String text);
    }
}
