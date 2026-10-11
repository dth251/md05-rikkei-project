package com.app.order.dto.response;

import java.time.Instant;

public record ShipmentResponse(
        String carrier,
        String trackingNumber,
        Instant shippedAt,
        Instant completedAt
) {
}
