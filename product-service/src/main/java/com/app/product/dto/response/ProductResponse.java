package com.app.product.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal unitPrice,
        String category,
        String brand,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
