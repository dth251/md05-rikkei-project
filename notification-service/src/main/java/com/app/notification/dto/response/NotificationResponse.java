package com.app.notification.dto.response;

import java.time.Instant;

public record NotificationResponse(
        String eventId,
        Long orderId,
        Long userId,
        String eventType,
        Instant createdAt
) {
}
