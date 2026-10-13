package com.app.order.config;

import com.app.order.service.ReservationExpiryService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "commerce.reservation.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationExpiryScheduler.class);
    private final ReservationExpiryService expiry;
    private final MeterRegistry registry;
    private final ReservationPolicy policy;

    public ReservationExpiryScheduler(
        ReservationExpiryService expiry,
        MeterRegistry registry,
        ReservationPolicy policy
    ) {
        this.expiry = expiry;
        this.registry = registry;
        this.policy = policy;
    }

    @Scheduled(fixedDelayString = "${commerce.reservation.scan-delay-ms:1000}")
    public void scan() {
        Instant cutoff = policy.cutoff(Instant.now());
        for (Long orderId : expiry.candidates(cutoff)) {
            try {
                if (expiry.expire(orderId, cutoff)) {
                    registry.counter("commerce.reservation.expired").increment();
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Reservation expiration deferred; inventory remains transactional");
            }
        }
    }
}
