package com.app.payment.dto.request;

import java.time.Instant;

public record VnpayCallback(
        String reference,
        String amountMinor,
        boolean successful,
        String transactionNumber,
        Instant paidAt
) {
}
