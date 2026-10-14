package com.app.payment.dto.response;

import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import java.math.BigDecimal;

public record VnpayReturnResponse(
        String reference,
        String providerOutcome,
        String message,
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        boolean reservationExpired,
        RefundStatus refundStatus
) {
}
