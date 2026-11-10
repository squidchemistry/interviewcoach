package com.aicoach.health.indicator;

import java.util.List;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reports DOWN when the pgvector extension is missing. Similar-question search depends on it, so a
 * database without it must not be considered ready. Registered as the {@code pgvector} component.
 */
@Component("pgvector")
public class PgVectorHealthIndicator extends AbstractHealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorHealthIndicator(JdbcTemplate jdbcTemplate) {
        super("pgvector health check failed");
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        if (versions.isEmpty()) {
            builder.down().withDetail("reason", "pgvector extension is not installed");
        } else {
            builder.up().withDetail("version", versions.getFirst());
        }
    }
}
