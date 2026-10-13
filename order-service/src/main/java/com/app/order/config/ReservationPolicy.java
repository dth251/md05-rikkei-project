package com.app.order.config;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReservationPolicy {

    private final Duration ttl;

    public ReservationPolicy(
            @Value("${commerce.reservation.ttl:PT5M}") Duration ttl
    ) {
        if (ttl.compareTo(Duration.ofMinutes(1)) < 0 || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Reservation TTL must be between one minute and one day");
        }
        this.ttl = ttl;
    }

    public Instant cutoff(
            Instant now
    ) {
        return now.minus(ttl);
    }
}
