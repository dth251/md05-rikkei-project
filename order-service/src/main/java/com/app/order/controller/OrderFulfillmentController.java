package com.app.order.controller;

import com.app.order.dto.request.ShipOrderRequest;
import com.app.order.dto.response.ApiResponse;
import com.app.order.dto.response.OrderResponse;
import com.app.order.service.OrderFulfillmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{id}")
public class OrderFulfillmentController {

    private final OrderFulfillmentService fulfillment;

    public OrderFulfillmentController(
            OrderFulfillmentService fulfillment
    ) {
        this.fulfillment = fulfillment;
    }

    @PostMapping("/ship")
    public ResponseEntity<ApiResponse<OrderResponse>> ship(
            @PathVariable Long id,
            @Valid @RequestBody ShipOrderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(fulfillment.ship(id, request), "Shipment recorded"));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<OrderResponse>> complete(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(fulfillment.complete(id), "Order completed"));
    }
}
