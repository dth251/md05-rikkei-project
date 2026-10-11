package com.app.notification.dto.event;

import java.math.BigDecimal;
import java.time.Instant;

public record CommerceEvent(
        String eventId,
        String type,
        Long orderId,
        Long userId,
        BigDecimal amount,
        Long paymentId,
        Instant occurredAt
) {
}
