package com.app.payment.dto.response;

public record VnpayReturnResponse(
        String reference,
        String providerOutcome,
        String message
) {
}
