package com.app.payment.dto.response;

import com.app.payment.entity.VnpayRefundState;
import java.time.Instant;

public record VnpayRefundResponse(
        Long paymentId,
        String requestId,
        VnpayRefundState state,
        String providerResponseCode,
        Instant requestedAt,
        Instant checkedAt
) {
}
