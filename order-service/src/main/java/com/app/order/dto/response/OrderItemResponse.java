package com.app.order.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String sku,
        String name,
        BigDecimal unitPrice,
        int quantity
) {
}
