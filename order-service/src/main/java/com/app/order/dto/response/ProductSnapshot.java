package com.app.order.dto.response;

import java.math.BigDecimal;

public record ProductSnapshot(
        Long id,
        String sku,
        String name,
        BigDecimal unitPrice
) {
}
