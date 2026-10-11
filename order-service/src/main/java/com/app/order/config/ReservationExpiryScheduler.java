package com.app.order.config;

import com.app.order.service.ReservationExpiryService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final Duration ttl;

    public ReservationExpiryScheduler(
        ReservationExpiryService expiry,
        MeterRegistry registry,
        @Value("${commerce.reservation.ttl:PT30M}") Duration ttl
    ) {
        if (ttl.compareTo(Duration.ofMinutes(1)) < 0 || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Reservation TTL must be between one minute and one day");
        }
        this.expiry = expiry;
        this.registry = registry;
        this.ttl = ttl;
    }

    @Scheduled(fixedDelayString = "${commerce.reservation.scan-delay-ms:10000}")
    public void scan() {
        Instant cutoff = Instant.now().minus(ttl);
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
