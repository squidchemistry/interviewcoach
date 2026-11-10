package com.aicoach.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicoach.support.PostgresContainerConfiguration;
import com.aicoach.support.RedisContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Runs real servers on random ports: actuator (probes, Prometheus) is reachable on the management
 * port — which is what container healthchecks use — and is NOT served on the public API port.
 */
@AutoConfigureObservability(tracing = false) // tests disable metrics export by default
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class ManagementPortIT {

    @LocalServerPort
    int serverPort;

    @LocalManagementPort
    int managementPort;

    private final RestClient client = RestClient.builder()
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
            .build();

    @Test
    void probesAndMetricsAreServedOnTheManagementPort() {
        ResponseEntity<String> readiness = get(managementPort, "/actuator/health/readiness");
        assertThat(readiness.getStatusCode().value()).isEqualTo(200);
        assertThat(readiness.getBody()).contains("\"status\":\"UP\"", "pgvector");

        assertThat(get(managementPort, "/actuator/health/liveness").getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> prometheus = get(managementPort, "/actuator/prometheus");
        assertThat(prometheus.getStatusCode().value()).isEqualTo(200);
        assertThat(prometheus.getBody()).contains("jvm_memory_used_bytes", "application=\"ai-interview-coach\"");
    }

    @Test
    void actuatorIsNotExposedOnThePublicPort() {
        assertThat(get(serverPort, "/actuator/health").getStatusCode().value()).isIn(401, 404);
        assertThat(get(serverPort, "/actuator/prometheus").getStatusCode().value()).isIn(401, 404);
        assertThat(get(serverPort, "/api/v1/health").getStatusCode().value()).isEqualTo(200);
    }

    private ResponseEntity<String> get(int port, String path) {
        return client.get().uri("http://localhost:" + port + path).retrieve().toEntity(String.class);
    }
}
