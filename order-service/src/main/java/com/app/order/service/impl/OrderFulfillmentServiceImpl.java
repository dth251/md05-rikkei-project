package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.event.CommerceEvent;
import com.app.order.dto.request.ShipOrderRequest;
import com.app.order.dto.response.OrderResponse;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.PaymentMethod;
import com.app.order.repository.StockRepository;
import java.util.Comparator;
import com.app.order.mapper.OrderMapper;
import com.app.order.mapper.ShippingAddressMapper;
import com.app.order.repository.OrderRepository;
import com.app.order.service.OrderFulfillmentService;
import com.app.order.service.OutboxService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderFulfillmentServiceImpl implements OrderFulfillmentService {

    private final OrderRepository orders;
    private final OrderMapper mapper;
    private final ShippingAddressMapper addresses;
    private final OutboxService outbox;
    private final StockRepository stocks;

    public OrderFulfillmentServiceImpl(
            OrderRepository orders,
            OrderMapper mapper,
            ShippingAddressMapper addresses,
            OutboxService outbox,
            StockRepository stocks
    ) {
        this.orders = orders;
        this.mapper = mapper;
        this.addresses = addresses;
        this.outbox = outbox;
        this.stocks = stocks;
    }

    @Override
    @Transactional
    public OrderResponse ship(
            Long orderId,
            ShipOrderRequest request
    ) {
        PurchaseOrder order = locked(orderId);
        var address = addresses.toEntity(request.shippingAddress());
        if (address != null && order.getShippingAddress() != null && !order.getShippingAddress().equals(address)) {
            throw new ApiException(HttpStatus.CONFLICT, "Shipping address snapshot cannot change");
        }
        String carrier = request.carrier().strip();
        String tracking = request.trackingNumber().strip();
        if (order.getStatus() == OrderStatus.SHIPPING || order.getStatus() == OrderStatus.COMPLETED) {
            if (!carrier.equals(order.getCarrier()) || !tracking.equals(order.getTrackingNumber())) {
                throw new ApiException(HttpStatus.CONFLICT, "Shipment already recorded with different details");
            }
            return mapper.toResponse(order);
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only confirmed orders can ship");
        }
        if (order.getShippingAddress() == null) {
            if (address == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Shipping address required for this legacy order");
            }
            order.setShippingAddress(address);
        }
        Instant now = Instant.now();
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            finalizeCodStock(order, true);
        }
        order.ship(carrier, tracking, now);
        append(order, "order.shipped", now);
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            append(order, "order.cod-shipped", now);
        }
        orders.flush();
        return mapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse complete(
            Long orderId
    ) {
        PurchaseOrder order = locked(orderId);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            return mapper.toResponse(order);
        }
        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only shipped orders can complete");
        }
        if (order.getPaymentMethod() == PaymentMethod.COD && order.getCodPaidAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "COD collection has not been acknowledged yet");
        }
        Instant now = Instant.now();
        order.complete(now);
        append(order, "order.completed", order.getCompletedAt());
        orders.flush();
        return mapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelCod(Long orderId) {
        PurchaseOrder order = locked(orderId);
        if (order.getPaymentMethod() != PaymentMethod.COD) {
            throw new ApiException(HttpStatus.CONFLICT, "Only COD orders can use this cancellation");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return mapper.toResponse(order);
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "COD cancellation is allowed only before shipment");
        }
        finalizeCodStock(order, false);
        order.cancelCod();
        append(order, "order.cod-cancelled", Instant.now());
        orders.flush();
        return mapper.toResponse(order);
    }

    private void finalizeCodStock(PurchaseOrder order, boolean successful) {
        for (var item : order.getItems().stream().sorted(Comparator.comparing(i -> i.getProductId())).toList()) {
            if (stocks.finalizeReservation(item.getProductId(), item.getQuantity(), successful) != 1) {
                throw new IllegalStateException("Reserved COD stock inconsistent; transaction rolled back");
            }
        }
    }

    private PurchaseOrder locked(
            Long orderId
    ) {
        return orders.findLockedById(orderId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void append(
            PurchaseOrder order,
            String type,
            Instant occurredAt
    ) {
        outbox.append(new CommerceEvent(UUID.randomUUID().toString(), type, order.getId(), order.getUserId(),
                order.getTotalAmount(), null, occurredAt));
    }
}
