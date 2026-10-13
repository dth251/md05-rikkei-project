package com.app.notification.dto.response;

public record EmailTask(
        String deliveryId,
        Long orderId,
        Long userId,
        String eventType,
        int attempts
) {
}
