package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.request.CreateOrderRequest;
import com.app.order.dto.response.OrderCreationResult;
import com.app.order.dto.response.OrderResponse;
import com.app.order.service.OrderService;
import com.app.order.service.OrderTransactions;
import com.app.order.service.ProductCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderTransactions orderTransactions;
    private final ProductCatalog productCatalog;

    public OrderServiceImpl(OrderTransactions orderTransactions, ProductCatalog productCatalog) {
        this.orderTransactions = orderTransactions;
        this.productCatalog = productCatalog;
    }

    @Override
    public OrderCreationResult create(Long userId, CreateOrderRequest request) {
        if (request.items().stream().map(item -> item.productId()).distinct().count() != request.items().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate product in order");
        }
        String fingerprint = fingerprint(request);
        var existing = orderTransactions.replay(userId, request, fingerprint);
        if (existing.isPresent()) {
            return new OrderCreationResult(existing.get(), false);
        }
        var products = request.items().stream().map(item -> productCatalog.get(item.productId())).toList();
        try {
            return new OrderCreationResult(orderTransactions.create(userId, request, fingerprint, products), true);
        } catch (DataIntegrityViolationException exception) {
            // This read runs after the failed transaction has rolled back, never in an aborted transaction.
            return orderTransactions.replay(userId, request, fingerprint)
                    .map(order -> new OrderCreationResult(order, false)).orElseThrow(() -> exception);
        }
    }

    @Override
    public OrderResponse get(Long orderId, Long userId, boolean administrator) {
        return orderTransactions.get(orderId, userId, administrator);
    }

    private String fingerprint(CreateOrderRequest request) {
        StringBuilder canonical = new StringBuilder(request.channel().name());
        request.items().stream().sorted(Comparator.comparing(item -> item.productId()))
                .forEach(item -> canonical.append('|').append(item.productId()).append(':').append(item.quantity()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
