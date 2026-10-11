package com.app.order.dto.response;

public record OrderCreationResult(
        OrderResponse order,
        boolean created
) {
}
