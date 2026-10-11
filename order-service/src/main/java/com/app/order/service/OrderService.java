package com.app.order.service;

import com.app.order.dto.request.CreateOrderRequest;
import com.app.order.dto.response.OrderCreationResult;
import com.app.order.dto.response.OrderResponse;

public interface OrderService {

    OrderCreationResult create(Long userId, CreateOrderRequest request);

    OrderResponse get(Long orderId, Long userId, boolean administrator);
}
