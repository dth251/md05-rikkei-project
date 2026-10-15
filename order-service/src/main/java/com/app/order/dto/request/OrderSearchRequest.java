package com.app.order.dto.request;

import com.app.order.entity.OrderStatus;
import com.app.order.entity.SalesChannel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OrderSearchRequest(
        @Min(0) @Max(10000) Integer page,
        @Min(1) @Max(100) Integer size,
        OrderStatus status,
        SalesChannel channel
) {
    public int pageValue() {
        return page == null ? 0 : page;
    }

    public int sizeValue() {
        return size == null ? 20 : size;
    }
}
