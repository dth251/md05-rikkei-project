package com.app.order.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "commerce.backlog-metrics.enabled", havingValue = "true", matchIfMissing = true)
public class BacklogMetrics {

    private final JdbcTemplate jdbc;
    private volatile long pending;
    private volatile Instant oldest;
    private volatile int valid;

    public BacklogMetrics(
            JdbcTemplate jdbc,
            MeterRegistry registry
    ) {
        this.jdbc = jdbc;
        Gauge.builder("commerce.outbox.pending", this, value -> value.pending).register(registry);
        Gauge.builder("commerce.outbox.oldest.seconds", this, value -> value.oldest == null ? 0
                : Math.max(0, Duration.between(value.oldest, Instant.now()).toSeconds())).register(registry);
        Gauge.builder("commerce.backlog.snapshot.valid", this, value -> value.valid).register(registry);
    }

    @Scheduled(fixedDelayString = "${commerce.backlog-metrics.delay-ms:10000}")
    public void sample() {
        try {
            jdbc.query("SELECT count(*),min(created_at) FROM outbox_events WHERE published_at IS NULL", row -> {
                pending = row.getLong(1);
                var timestamp = row.getTimestamp(2);
                oldest = timestamp == null ? null : timestamp.toInstant();
                valid = 1;
            });
        } catch (org.springframework.dao.DataAccessException exception) {
            valid = 0;
        }
    }
}
