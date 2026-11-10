package com.aicoach.execution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/** Factory: chooses the code executor from configuration ({@code CODE_EXECUTOR=judge0|mock}). */
@Configuration(proxyBeanMethods = false)
public class ExecutionConfig {

    @Validated
    @ConfigurationProperties(prefix = "app.execution")
    public record ExecutionProperties(
            @NotBlank @Pattern(regexp = "mock|judge0") String provider,
            @NotBlank String judge0Url,
            String judge0AuthHeader,
            String judge0AuthToken,
            String rapidapiHost,
            @NotNull Duration timeout) {
    }

    @Bean
    CodeExecutor codeExecutor(ExecutionProperties properties, ObjectMapper json, Environment environment) {
        if ("judge0".equals(properties.provider())) {
            Map<String, String> headers = new LinkedHashMap<>();
            if (StringUtils.hasText(properties.rapidapiHost())) {
                headers.put("X-RapidAPI-Key", properties.judge0AuthToken());
                headers.put("X-RapidAPI-Host", properties.rapidapiHost());
            } else if (StringUtils.hasText(properties.judge0AuthToken())) {
                headers.put(properties.judge0AuthHeader(), properties.judge0AuthToken());
            }
            return new Judge0CodeExecutor(properties.judge0Url(), headers, properties.timeout(), json, 200);
        }
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("CODE_EXECUTOR=mock is not allowed with the prod profile");
        }
        return new MockCodeExecutor();
    }
}
