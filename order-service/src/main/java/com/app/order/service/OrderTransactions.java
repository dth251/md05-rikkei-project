package com.app.order.service;

import com.app.order.dto.request.CreateOrderRequest;
import com.app.order.dto.response.OrderResponse;
import com.app.order.dto.response.ProductSnapshot;
import java.util.List;
import java.util.Optional;

public interface OrderTransactions {

    Optional<OrderResponse> replay(Long userId, CreateOrderRequest request, String fingerprint);

    OrderResponse create(Long userId, CreateOrderRequest request, String fingerprint, List<ProductSnapshot> products);

    OrderResponse get(Long orderId, Long userId, boolean administrator);
}
