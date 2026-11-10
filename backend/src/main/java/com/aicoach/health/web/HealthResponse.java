package com.aicoach.health.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Readiness summary for clients and the web app's status indicator")
public record HealthResponse(
        @Schema(example = "UP") String status,
        @Schema(example = "0.1.0") String version,
        @Schema(example = "{\"db\":\"UP\",\"redis\":\"UP\",\"pgvector\":\"UP\"}") Map<String, String> components) {
}
