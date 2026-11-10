package com.aicoach.ai.service;

import com.aicoach.ai.AiException;
import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.AiTypes.ChatMessage;
import com.aicoach.ai.AiTypes.EmbeddingProvider;
import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import com.aicoach.ai.AiTypes.ModelTier;
import com.aicoach.ai.service.prompt.PromptRegistry;
import com.aicoach.ai.service.prompt.PromptRegistry.RenderedPrompt;
import com.aicoach.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Facade over the AI layer: every LLM call goes through here. It renders the versioned prompt, picks the model for
 * the prompt's tier, calls the decorated provider (metering → cache → circuit breaker → retry → vendor), and for JSON
 * prompts parses and validates the output, asking the model to repair it at most twice.
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    static final int MAX_REPAIRS = 2;

    private final LlmProvider provider;
    private final PromptRegistry prompts;
    private final AiProperties properties;
    private final EmbeddingProvider embeddings;
    private final ObjectMapper json;
    private final Validator validator;

    public AiGateway(LlmProvider provider, PromptRegistry prompts, AiProperties properties, EmbeddingProvider embeddings,
            ObjectMapper json, Validator validator) {
        this.provider = provider;
        this.prompts = prompts;
        this.properties = properties;
        this.embeddings = embeddings;
        this.json = json;
        this.validator = validator;
    }

    public String providerName() {
        return provider.name();
    }

    public String modelFor(ModelTier tier) {
        return tier == ModelTier.SMART ? properties.modelSmart() : properties.modelFast();
    }

    public LlmResult complete(String promptId, Map<String, Object> variables, CallContext context) {
        return provider.complete(request(promptId, variables, context));
    }

    public LlmResult stream(String promptId, Map<String, Object> variables, CallContext context, Consumer<String> onDelta) {
        return provider.stream(request(promptId, variables, context), onDelta);
    }

    /**
     * Calls a JSON prompt and maps the answer to {@code type}, validating it with Bean Validation. Invalid output is
     * sent back with the list of problems for a repair (max {@value #MAX_REPAIRS}); then {@code AI_INVALID_OUTPUT}.
     */
    public <T> StructuredResult<T> structured(String promptId, Map<String, Object> variables, Class<T> type,
            CallContext context) {
        LlmResult result = complete(promptId, variables, context);
        String text = result.text();
        List<String> problems = List.of();
        for (int attempt = 0; attempt <= MAX_REPAIRS; attempt++) {
            if (attempt > 0) {
                Map<String, Object> repairVars = new HashMap<>();
                repairVars.put("outputSchema", variables.getOrDefault("outputSchema", ""));
                repairVars.put("invalidOutput", text.length() > 12_000 ? text.substring(0, 12_000) : text);
                repairVars.put("problems", String.join("\n", problems));
                CallContext repairContext = new CallContext(context.userId(), context.sessionId(),
                        AiPurpose.EVALUATION_REPAIR, context.seed() + attempt);
                result = complete("json-repair", repairVars, repairContext);
                text = result.text();
            }
            ParseOutcome<T> outcome = parse(text, type);
            if (outcome.value() != null) {
                return new StructuredResult<>(outcome.value(), result, attempt);
            }
            problems = outcome.problems();
            log.info("AI output for {} failed validation (attempt {}): {}", promptId, attempt + 1, problems);
        }
        throw new AiException(ErrorCode.AI_INVALID_OUTPUT, "The AI returned output that did not match the expected format",
                "INVALID_OUTPUT");
    }

    public record StructuredResult<T>(T value, LlmResult raw, int repairs) {
    }

    record ParseOutcome<T>(T value, List<String> problems) {
    }

    <T> ParseOutcome<T> parse(String text, Class<T> type) {
        String body = stripFences(text);
        T value;
        try {
            value = json.readValue(body, type);
        } catch (Exception e) {
            return new ParseOutcome<>(null, List.of("not valid JSON for the schema: " + firstLine(e.getMessage())));
        }
        if (value == null) {
            return new ParseOutcome<>(null, List.of("empty JSON"));
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            List<String> problems = new ArrayList<>();
            violations.forEach(v -> problems.add(v.getPropertyPath() + ": " + v.getMessage()));
            problems.sort(String::compareTo);
            return new ParseOutcome<>(null, problems);
        }
        return new ParseOutcome<>(value, List.of());
    }

    static String stripFences(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }

    public float[] embed(String text) {
        return embeddings.embed(text);
    }

    public String embeddingModel() {
        return embeddings.model();
    }

    LlmRequest request(String promptId, Map<String, Object> variables, CallContext context) {
        RenderedPrompt rendered = prompts.render(promptId, variables);
        var template = rendered.template();
        return new LlmRequest(template.id(), template.versionLabel(), template.tier(), modelFor(template.tier()),
                rendered.system(), List.of(ChatMessage.user(rendered.user())), template.temperature(),
                template.maxTokens(), template.json(), Map.copyOf(withoutNulls(variables)), context);
    }

    private static Map<String, Object> withoutNulls(Map<String, Object> variables) {
        Map<String, Object> copy = new HashMap<>();
        variables.forEach((k, v) -> {
            if (v != null) {
                copy.put(k, v);
            }
        });
        return copy;
    }
}
