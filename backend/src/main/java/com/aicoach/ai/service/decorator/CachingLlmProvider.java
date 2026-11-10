package com.aicoach.ai.service.decorator;

import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.AiTypes.LlmRequest;
import com.aicoach.ai.AiTypes.LlmResult;
import com.aicoach.common.security.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Decorator: caches identical deterministic (temperature 0, non-streaming) calls in Redis. The key hashes the
 * provider, model, prompt id+version and the full rendered input, so a prompt change never serves stale output.
 * Cache failures are logged and ignored; caching must never break a request.
 */
public class CachingLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(CachingLlmProvider.class);
    static final String PREFIX = "ai:cache:";

    private final LlmProvider inner;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public CachingLlmProvider(LlmProvider inner, StringRedisTemplate redis, ObjectMapper json, Duration ttl) {
        this.inner = inner;
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    @Override
    public String name() {
        return inner.name();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        if (request.temperature() != 0.0) {
            return inner.complete(request);
        }
        String key = key(request);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return json.readValue(cached, LlmResult.class).asCached();
            }
        } catch (Exception e) {
            log.warn("AI cache read failed: {}", e.toString());
        }
        LlmResult result = inner.complete(request);
        try {
            redis.opsForValue().set(key, json.writeValueAsString(result), ttl);
        } catch (Exception e) {
            log.warn("AI cache write failed: {}", e.toString());
        }
        return result;
    }

    @Override
    public LlmResult stream(LlmRequest request, Consumer<String> onDelta) {
        return inner.stream(request, onDelta);
    }

    String key(LlmRequest request) {
        try {
            String material = String.join("\u0000", inner.name(), request.model(), request.promptId(),
                    request.promptVersion(), request.system(), json.writeValueAsString(request.messages()),
                    Integer.toString(request.maxTokens()), Boolean.toString(request.json()));
            return PREFIX + Hashing.sha256Hex(material);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build AI cache key", e);
        }
    }
}
