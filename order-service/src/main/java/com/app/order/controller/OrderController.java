package com.app.order.controller;

import com.app.order.dto.request.CreateOrderRequest;
import com.app.order.dto.request.OrderSearchRequest;
import com.app.order.dto.response.ApiResponse;
import com.app.order.dto.response.OrderResponse;
import com.app.order.dto.response.OrderPageResponse;
import com.app.order.service.OrderService;
import com.app.order.service.OrderQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService queries;

    public OrderController(OrderService orderService, OrderQueryService queries) {
        this.orderService = orderService;
        this.queries = queries;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OrderPageResponse>> list(
            @org.springdoc.core.annotations.ParameterObject
            @Valid @ModelAttribute OrderSearchRequest request,
            Authentication authentication
    ) {
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success(queries.list(
                Long.valueOf(authentication.getName()), administrator, request), "Orders retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        var result = orderService.create(Long.valueOf(authentication.getName()), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result.order(), result.created() ? "Order created" : "Order replayed"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> get(
            @PathVariable Long id,
            Authentication authentication
    ) {
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success(
                orderService.get(id, Long.valueOf(authentication.getName()), administrator), "Order retrieved"
        ));
    }
}
