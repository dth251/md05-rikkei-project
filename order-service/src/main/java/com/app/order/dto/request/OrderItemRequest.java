package com.app.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull
        @Positive
        Long productId,

        @Positive
        @Max(10000)
        int quantity
) {
}
