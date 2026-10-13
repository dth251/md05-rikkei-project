package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.request.CreateOrderRequest;
import com.app.order.dto.event.CommerceEvent;
import com.app.order.dto.response.OrderResponse;
import com.app.order.dto.response.ProductSnapshot;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.PaymentMethod;
import com.app.order.mapper.OrderMapper;
import com.app.order.mapper.ShippingAddressMapper;
import com.app.order.repository.OrderRepository;
import com.app.order.repository.StockRepository;
import com.app.order.service.OrderTransactions;
import com.app.order.service.OutboxService;
import java.time.Instant;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderTransactionsImpl implements OrderTransactions {

    private final OrderRepository orderRepository;
    private final StockRepository stockRepository;
    private final OrderMapper orderMapper;
    private final OutboxService outboxService;
    private final ShippingAddressMapper addresses;

    public OrderTransactionsImpl(
        OrderRepository orderRepository,
        StockRepository stockRepository,
        OrderMapper orderMapper,
        OutboxService outboxService,
        ShippingAddressMapper addresses
    ) {
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.orderMapper = orderMapper;
        this.outboxService = outboxService;
        this.addresses = addresses;
    }

    @Override
    public Optional<OrderResponse> replay(
        Long userId,
        CreateOrderRequest request,
        String fingerprint
    ) {
        return orderRepository.findByUserIdAndIdempotencyKey(userId, request.idempotencyKey()).map(order -> {
            if (!fingerprint.equals(order.getRequestFingerprint())) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key already used with another request");
            }
            return orderMapper.toResponse(order);
        });
    }

    @Override
    @Transactional
    public OrderResponse create(
        Long userId,
        CreateOrderRequest request,
        String fingerprint,
        List<ProductSnapshot> products
    ) {
        PurchaseOrder order = new PurchaseOrder(userId, request.idempotencyKey(), request.channel());
        order.setRequestFingerprint(fingerprint);
        order.setShippingAddress(addresses.toEntity(request.shippingAddress()));
        if (request.paymentMethod() == PaymentMethod.COD) {
            if (request.shippingAddress() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COD requires shipping address");
            }
            order.confirmCod();
        }
        for (int index = 0; index < request.items().size(); index++) {
            var product = products.get(index);
            order.addItem(product.id(), product.sku(), product.name(), product.unitPrice(), request.items().get(index).quantity());
        }
        if (order.getTotalAmount().precision() - order.getTotalAmount().scale() > 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Order total exceeds supported amount");
        }
        // Flush the unique request key before reserving stock; duplicates wait for the first transaction.
        orderRepository.saveAndFlush(order);
        for (var item : request.items().stream().sorted(Comparator.comparing(i -> i.productId())).toList()) {
            if (stockRepository.reserve(item.productId(), item.quantity()) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock");
            }
        }
        String eventType = order.getPaymentMethod() == PaymentMethod.COD ? "order.cod-created" : "order.created";
        outboxService.append(new CommerceEvent(UUID.randomUUID().toString(), eventType, order.getId(),
                order.getUserId(), order.getTotalAmount(), null, Instant.now()));
        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponse get(
        Long orderId,
        Long userId,
        boolean administrator
    ) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!administrator && !userId.equals(order.getUserId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return orderMapper.toResponse(order);
    }
}
