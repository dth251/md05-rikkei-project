package com.app.notification.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class EmailMetrics {

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, Long> counts = new ConcurrentHashMap<>();
    private volatile int valid;

    public EmailMetrics(
            JdbcTemplate jdbc,
            MeterRegistry metrics
    ) {
        this.jdbc = jdbc;
        for (String state : List.of("PENDING", "SENDING", "RETRY", "SENT", "FAILED", "UNKNOWN")) {
            counts.put(state, 0L);
            Gauge.builder("commerce.email.deliveries", counts, values -> values.get(state))
                    .tag("state", state).register(metrics);
        }
        Gauge.builder("commerce.email.snapshot.valid", this, value -> value.valid).register(metrics);
    }

    @Scheduled(fixedDelayString = "${notification.email.metrics-delay-ms:10000}")
    public void sample() {
        try {
            var snapshot = new java.util.HashMap<String, Long>();
            jdbc.query("SELECT state,count(*) FROM email_deliveries GROUP BY state", row -> {
                snapshot.put(row.getString(1), row.getLong(2));
            });
            counts.replaceAll((state, ignored) -> snapshot.getOrDefault(state, 0L));
            valid = 1;
        } catch (org.springframework.dao.DataAccessException exception) {
            valid = 0;
        }
    }
}
