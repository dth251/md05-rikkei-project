package com.app.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockRequest(
        @NotNull
        @Positive
        Long productId,

        @Positive
        @Max(1000000)
        int quantity
) {
}
