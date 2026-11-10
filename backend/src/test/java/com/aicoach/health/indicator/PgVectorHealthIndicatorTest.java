package com.aicoach.health.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class PgVectorHealthIndicatorTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PgVectorHealthIndicator indicator = new PgVectorHealthIndicator(jdbc);

    @Test
    void upWithVersionWhenExtensionInstalled() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("0.8.0"));
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("version", "0.8.0");
    }

    @Test
    void downWhenExtensionMissing() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void downWhenDatabaseUnreachable() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
