package com.app.order.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservationDeadline {

    private final Duration ttl;

    public ReservationDeadline(
            @Value("${commerce.reservation.ttl:PT200S}") Duration ttl
    ) {
        if (ttl.compareTo(Duration.ofMinutes(1)) < 0 || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Reservation TTL must be between one minute and one day");
        }
        this.ttl = ttl;
    }

    @Bean
    public Clock reservationClock() {
        return Clock.systemUTC();
    }

    public Instant cutoff(Clock clock) {
        return clock.instant().minus(ttl);
    }
}
