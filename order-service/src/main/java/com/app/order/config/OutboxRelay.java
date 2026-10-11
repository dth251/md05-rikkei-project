package com.app.order.config;

import com.app.order.service.OutboxService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "commerce.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxService outboxService;
    private final Counter failures;
    private final Counter completed;
    private final Timer duration;
    private final int maxEvents;
    private final long budgetNanos;

    public OutboxRelay(
        OutboxService outboxService,
        MeterRegistry registry,
        @Value("${commerce.outbox.max-events-per-tick:200}") int maxEvents,
        @Value("${commerce.outbox.budget-ms:2000}") long budgetMs
    ) {
        this.outboxService = outboxService;
        this.failures = registry.counter("commerce.outbox.publish", "result", "failed");
        this.completed = registry.counter("commerce.outbox.publish", "result", "completed");
        this.duration = registry.timer("commerce.outbox.relay");
        this.maxEvents = Math.clamp(maxEvents, 1, 2000);
        this.budgetNanos = Math.clamp(budgetMs, 100, 10000) * 1_000_000;
    }

    @Scheduled(fixedDelayString = "${commerce.outbox.delay-ms:1000}")
    public void relay() {
        duration.record(this::drain);
    }

    private void drain() {
        long deadline = System.nanoTime() + budgetNanos;
        int handled = 0;
        while (handled < maxEvents && System.nanoTime() < deadline) {
            var pending = outboxService.pending();
            if (pending.isEmpty()) {
                return;
            }
            for (String eventId : pending) {
                if (handled >= maxEvents || System.nanoTime() >= deadline) {
                    return;
                }
                handled++;
                try {
                    outboxService.publish(eventId);
                    completed.increment();
                } catch (RuntimeException exception) {
                    failures.increment();
                    // Failure metadata commits separately from the rolled-back publish transaction.
                    outboxService.defer(eventId);
                    LOGGER.warn("Commerce outbox event deferred; relay continues");
                }
            }
        }
    }
}
