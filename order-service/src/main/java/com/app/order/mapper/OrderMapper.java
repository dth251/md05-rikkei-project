package com.app.order.mapper;

import com.app.order.dto.response.OrderItemResponse;
import com.app.order.dto.response.OrderResponse;
import com.app.order.dto.response.ShipmentResponse;
import com.app.order.entity.PurchaseOrder;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    private final ShippingAddressMapper addresses;

    public OrderMapper(
            ShippingAddressMapper addresses
    ) {
        this.addresses = addresses;
    }

    public OrderResponse toResponse(PurchaseOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getChannel(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().stream().map(item -> new OrderItemResponse(
                        item.getProductId(), item.getSku(), item.getName(), item.getUnitPrice(), item.getQuantity()
                )).toList(),
                order.getCreatedAt(),
                addresses.toResponse(order.getShippingAddress()),
                order.getShippedAt() == null ? null : new ShipmentResponse(order.getCarrier(), order.getTrackingNumber(),
                        order.getShippedAt(), order.getCompletedAt()),
                order.getPaymentMethod(),
                order.getCodPaymentId(),
                order.getCodPaidAt()
        );
    }
}
