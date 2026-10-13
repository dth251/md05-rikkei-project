package com.app.payment.dto.request;

import java.util.Map;

public record VnpayLedgerRequest(
        Long paymentId,
        String refundRequestId,
        boolean send,
        Map<String, String> parameters
) {
    public VnpayLedgerRequest {
        parameters = Map.copyOf(parameters);
    }
}
