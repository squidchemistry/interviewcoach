package com.aicoach.ai.service;

import com.aicoach.ai.AiProperties;
import com.aicoach.ai.AiTypes.EmbeddingProvider;
import com.aicoach.ai.AiTypes.LlmProvider;
import com.aicoach.ai.repository.AiCallLogRepository;
import com.aicoach.ai.service.decorator.CachingLlmProvider;
import com.aicoach.ai.service.decorator.CircuitBreaker;
import com.aicoach.ai.service.decorator.CircuitBreakerLlmProvider;
import com.aicoach.ai.service.decorator.MeteringLlmProvider;
import com.aicoach.ai.service.decorator.RetryingLlmProvider;
import com.aicoach.ai.service.embedding.MockHashingEmbeddingProvider;
import com.aicoach.ai.service.embedding.OpenAiEmbeddingProvider;
import com.aicoach.ai.service.mock.MockLlmProvider;
import com.aicoach.ai.service.provider.AnthropicLlmProvider;
import com.aicoach.ai.service.provider.OpenAiCompatibleLlmProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * Factory for the AI provider chain. The vendor adapter is chosen from configuration (Strategy) and wrapped in
 * decorators, outermost first:
 * <pre>Metering(budget + logs) → Caching → CircuitBreaker → Retry(+timeout per attempt) → vendor | mock</pre>
 * Metering is outermost so cache hits and breaker rejections are recorded too; retry sits inside the breaker so one
 * logical call is one breaker sample.
 */
@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    CircuitBreaker aiCircuitBreaker(Clock clock) {
        return new CircuitBreaker(20, 10, 0.5, Duration.ofSeconds(30), clock);
    }

    @Bean
    LlmProvider llmProvider(AiProperties properties, ObjectMapper json, StringRedisTemplate redis,
            AiCallLogRepository logs, MeterRegistry meters, Clock clock, CircuitBreaker aiCircuitBreaker) {
        LlmProvider vendor = vendor(properties, json);
        LlmProvider chain = new RetryingLlmProvider(vendor, properties.maxRetries(), properties.retryBackoff());
        chain = new CircuitBreakerLlmProvider(chain, aiCircuitBreaker);
        chain = new CachingLlmProvider(chain, redis, json, properties.cacheTtl());
        return new MeteringLlmProvider(chain, logs, meters, clock, properties.sessionTokenBudget(),
                properties.dailyTokenBudget());
    }

    static LlmProvider vendor(AiProperties properties, ObjectMapper json) {
        return switch (properties.provider()) {
            case "anthropic" -> new AnthropicLlmProvider(requireKey(properties.apiKey(), "AI_API_KEY"),
                    properties.baseUrl(), properties.timeout(), json);
            case "openai" -> new OpenAiCompatibleLlmProvider(requireKey(properties.apiKey(), "AI_API_KEY"),
                    properties.baseUrl(), properties.timeout(), json);
            default -> new MockLlmProvider(json, properties.mockStreamDelayMs());
        };
    }

    @Bean
    EmbeddingProvider embeddingProvider(AiProperties properties, ObjectMapper json) {
        AiProperties.Embedding config = properties.embedding();
        if ("openai".equals(config.provider())) {
            return new OpenAiEmbeddingProvider(requireKey(config.apiKey(), "AI_EMBEDDING_API_KEY"), config.baseUrl(),
                    config.model(), json);
        }
        return new MockHashingEmbeddingProvider();
    }

    /** Readiness detail: which provider is active and the circuit-breaker state (never fails readiness). */
    @Bean("aiProvider")
    HealthIndicator aiProviderHealth(AiProperties properties, CircuitBreaker aiCircuitBreaker) {
        return () -> Health.up()
                .withDetail("provider", properties.provider())
                .withDetail("circuit", aiCircuitBreaker.state().name())
                .build();
    }

    private static String requireKey(String key, String envName) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException(envName + " is required for a real AI provider (or set AI_PROVIDER=mock)");
        }
        return key;
    }
}
