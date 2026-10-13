package com.app.order.dto.response;

import com.app.order.entity.ReturnStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderReturnResponse(
        Long orderId,
        Long userId,
        BigDecimal amount,
        ReturnStatus status,
        String reason,
        Instant requestedAt,
        Long decidedBy,
        Instant decidedAt,
        String decisionNote,
        Long receivedBy,
        Instant receivedAt,
        String warehouseReceipt,
        Boolean restock,
        Long paymentId,
        Instant refundedAt
) {
}
