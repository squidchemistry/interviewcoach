package com.aicoach.health.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, minimal readiness summary on the API port. Full actuator details stay on the internal
 * management port; this endpoint only exposes component names and their UP/DOWN status.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health")
public class HealthController {

    private static final String READINESS_GROUP = "readiness";

    private final HealthEndpoint healthEndpoint;
    private final String version;

    public HealthController(HealthEndpoint healthEndpoint, ObjectProvider<BuildProperties> buildProperties) {
        this.healthEndpoint = healthEndpoint;
        this.version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
    }

    @GetMapping
    @Operation(summary = "Readiness of the API and its dependencies")
    @ApiResponse(responseCode = "200", description = "All required dependencies are UP")
    @ApiResponse(responseCode = "503", description = "At least one required dependency is DOWN")
    public ResponseEntity<HealthResponse> health() {
        HealthComponent readiness = healthEndpoint.healthForPath(READINESS_GROUP);
        Map<String, String> components = new LinkedHashMap<>();
        if (readiness instanceof CompositeHealth composite && composite.getComponents() != null) {
            composite.getComponents().forEach((name, component) ->
                    components.put(name, component.getStatus().getCode()));
        }
        Status status = readiness == null ? Status.UNKNOWN : readiness.getStatus();
        HttpStatus httpStatus = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus)
                .cacheControl(CacheControl.noStore())
                .body(new HealthResponse(status.getCode(), version, components));
    }
}
