package com.app.payment.dto.request;

import java.util.Map;

public record VnpayLedgerRequest(
        Long paymentId,
        String requestId,
        boolean send,
        Map<String, String> parameters
) {
    public VnpayLedgerRequest {
        parameters = Map.copyOf(parameters);
    }
}
