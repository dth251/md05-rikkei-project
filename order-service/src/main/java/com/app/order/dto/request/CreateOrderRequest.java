package com.app.order.dto.request;

import com.app.order.entity.SalesChannel;
import com.app.order.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotNull
        SalesChannel channel,

        @NotBlank
        @Size(max = 128)
        String idempotencyKey,

        @NotNull
        @Size(min = 1, max = 50)
        List<@NotNull @Valid OrderItemRequest> items,

        @Valid
        ShippingAddressRequest shippingAddress,

        PaymentMethod paymentMethod
) {

    public CreateOrderRequest {
        paymentMethod = paymentMethod == null ? PaymentMethod.ONLINE : paymentMethod;
    }

    public CreateOrderRequest(
            SalesChannel channel,
            String idempotencyKey,
            List<OrderItemRequest> items,
            ShippingAddressRequest shippingAddress
    ) {
        this(channel, idempotencyKey, items, shippingAddress, PaymentMethod.ONLINE);
    }

    public CreateOrderRequest(
            SalesChannel channel,
            String idempotencyKey,
            List<OrderItemRequest> items
    ) {
        this(channel, idempotencyKey, items, null);
    }
}
