package com.app.order.controller;

import com.app.order.dto.request.DecideReturnRequest;
import com.app.order.dto.request.ReceiveReturnRequest;
import com.app.order.dto.request.RequestReturnRequest;
import com.app.order.dto.response.ApiResponse;
import com.app.order.dto.response.OrderReturnResponse;
import com.app.order.service.OrderReturnService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{id}/return")
public class OrderReturnController {

    private final OrderReturnService returns;

    public OrderReturnController(OrderReturnService returns) {
        this.returns = returns;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderReturnResponse>> request(
            @PathVariable Long id,
            @Valid @RequestBody RequestReturnRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(returns.request(id, userId(authentication),
                administrator(authentication), request), "Return requested"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OrderReturnResponse>> get(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(returns.get(id, userId(authentication),
                administrator(authentication)), "Return retrieved"));
    }

    @PostMapping("/decision")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> decide(
            @PathVariable Long id,
            @Valid @RequestBody DecideReturnRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(returns.decide(id, userId(authentication), request),
                "Return decision recorded"));
    }

    @PostMapping("/receive")
    public ResponseEntity<ApiResponse<OrderReturnResponse>> receive(
            @PathVariable Long id,
            @Valid @RequestBody ReceiveReturnRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(returns.receive(id, userId(authentication), request),
                "Returned goods received"));
    }

    private Long userId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    private boolean administrator(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
