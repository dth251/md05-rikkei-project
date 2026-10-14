package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.app.order.config.ReservationDeadline;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReservationDeadlineTests {

    @Test
    void deadlineIsInclusiveAt200SecondsAndInvalidConfigurationIsRejected() {
        var deadline = new ReservationDeadline(Duration.ofSeconds(200));
        Instant created = Instant.parse("2026-10-14T00:00:00Z");
        assertEquals(created, deadline.cutoff(Clock.fixed(created.plusSeconds(200), ZoneOffset.UTC)));
        assertEquals(created.minusNanos(1), deadline.cutoff(
                Clock.fixed(created.plusSeconds(200).minusNanos(1), ZoneOffset.UTC)));
        assertThrows(IllegalArgumentException.class, () -> new ReservationDeadline(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ReservationDeadline(Duration.ofDays(2)));
    }
}
