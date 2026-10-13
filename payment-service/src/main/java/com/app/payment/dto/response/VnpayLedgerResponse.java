package com.app.payment.dto.response;

import java.util.Map;

public record VnpayLedgerResponse(
        Map<String, String> parameters
) {
    public VnpayLedgerResponse {
        parameters = Map.copyOf(parameters);
    }
}
