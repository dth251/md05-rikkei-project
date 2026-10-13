package com.app.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CashRefundResponse(
        Long paymentId,
        BigDecimal amount,
        String receiptNumber,
        Long refundedBy,
        Instant refundedAt
) {
}
