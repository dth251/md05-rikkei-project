package com.app.order.dto.response;

import com.app.order.entity.OrderStatus;
import com.app.order.entity.SalesChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        SalesChannel channel,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt
) {
}
