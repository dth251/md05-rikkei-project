package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.app.order.config.ReservationPolicy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReservationPolicyTests {

    @Test
    void fiveMinutePolicyHasAnInclusiveDeadlineAndRejectsInvalidConfiguration() {
        var policy = new ReservationPolicy(Duration.ofMinutes(5));
        Instant created = Instant.parse("2026-10-14T00:00:00Z");
        assertEquals(created, policy.cutoff(created.plusSeconds(300)));
        assertEquals(created.minusNanos(1), policy.cutoff(created.plusSeconds(300).minusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> new ReservationPolicy(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ReservationPolicy(Duration.ofDays(2)));
    }
}
