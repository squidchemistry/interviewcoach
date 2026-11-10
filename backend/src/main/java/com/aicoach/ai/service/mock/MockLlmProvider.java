package com.aicoach.ai.service.mock;

import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;

/**
 * MOCK MODE ({@code AI_PROVIDER=mock}): a deterministic simulator so the whole product runs end-to-end with no paid
 * keys. It is not a canned string: {@link MockScenarios} reads the prompt's variables and produces realistic,
 * schema-valid output (heuristic scoring, evidence quoted from the real transcript, contextual follow-ups).
 * Randomness is seeded by session and turn, so demos and tests are reproducible. The UI shows a "Mock AI" badge.
 */
public class MockLlmProvider implements LlmProvider {

    private final MockScenarios scenarios;
    private final int streamDelayMs;

    public MockLlmProvider(ObjectMapper json, int streamDelayMs) {
        this.scenarios = new MockScenarios(json);
        this.streamDelayMs = streamDelayMs;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        String text = scenarios.respond(request);
        return new LlmResult(text, estimateTokens(request.system()) + estimateTokens(request.messages().getFirst().content()),
                estimateTokens(text), "mock-" + request.tier().name().toLowerCase(), false);
    }

    @Override
    public LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
        LlmResult result = complete(request);
        String text = result.text();
        int i = 0;
        int chunk = 3;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + chunk);
            onDelta.accept(text.substring(i, end));
            i = end;
            chunk = 3 + (i % 4);
            if (streamDelayMs > 0) {
                try {
                    Thread.sleep(streamDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return result;
    }

    static int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
