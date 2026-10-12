package com.app.order.service;

import com.app.order.dto.request.ShipOrderRequest;
import com.app.order.dto.response.OrderResponse;

public interface OrderFulfillmentService {

    OrderResponse ship(
            Long orderId,
            ShipOrderRequest request
    );

    OrderResponse complete(
            Long orderId
    );
}
