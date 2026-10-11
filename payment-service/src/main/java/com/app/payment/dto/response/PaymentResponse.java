package com.app.payment.dto.response;

import com.app.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt,
        Instant completedAt
) {
}
