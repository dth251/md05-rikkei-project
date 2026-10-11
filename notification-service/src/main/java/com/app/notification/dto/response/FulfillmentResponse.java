package com.app.notification.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record FulfillmentResponse(
        String eventId,
        Long orderId,
        Long userId,
        String eventType,
        BigDecimal amount,
        Instant occurredAt,
        Instant createdAt
) {
}
