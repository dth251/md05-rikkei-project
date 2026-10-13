package com.app.payment.dto.response;

import java.time.Instant;

public record VnpayCheckoutResponse(
        Long paymentId,
        String reference,
        String checkoutUrl,
        Instant expiresAt
) {
}
