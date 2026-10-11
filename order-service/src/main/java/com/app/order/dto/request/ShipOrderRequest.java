package com.app.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipOrderRequest(
        @NotBlank
        @Size(max = 100)
        String carrier,
        @NotBlank
        @Size(max = 128)
        String trackingNumber,
        @Valid
        ShippingAddressRequest shippingAddress
) {
}
