package com.app.order.dto.response;

import com.app.order.entity.OrderStatus;
import com.app.order.entity.PaymentMethod;
import com.app.order.entity.SalesChannel;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long id,
        Long userId,
        SalesChannel channel,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        PaymentMethod paymentMethod
) {
}
