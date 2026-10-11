package com.app.order.dto.response;

public record InventoryResponse(
        Long productId,
        int available,
        int reserved
) {
}
