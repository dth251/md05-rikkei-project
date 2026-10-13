package com.app.notification.dto.response;

public record EmailTask(
        String deliveryId,
        Long orderId,
        Long userId,
        String eventType,
        int attempts,
        String traceParent
) {
    public EmailTask(
            String deliveryId,
            Long orderId,
            Long userId,
            String eventType,
            int attempts
    ) {
        this(deliveryId, orderId, userId, eventType, attempts, null);
    }
}
