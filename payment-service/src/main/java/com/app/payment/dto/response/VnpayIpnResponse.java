package com.app.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayIpnResponse(
        @JsonProperty("RspCode")
        String responseCode,

        @JsonProperty("Message")
        String message
) {
}
