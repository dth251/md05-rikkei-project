package com.app.payment.controller;

import com.app.payment.dto.request.SimulatePaymentRequest;
import com.app.payment.dto.response.ApiResponse;
import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.service.PaymentService;
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
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.get(id, Long.valueOf(authentication.getName()),
                administrator(authentication)), "Payment retrieved"));
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrder(
            @PathVariable Long orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getByOrder(orderId, Long.valueOf(authentication.getName()),
                administrator(authentication)), "Payment retrieved"));
    }

    @PostMapping("/{id}/simulate")
    public ResponseEntity<ApiResponse<PaymentResponse>> simulate(
            @PathVariable Long id,
            @Valid @RequestBody SimulatePaymentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.simulate(id, request.status()), "Payment simulated"));
    }

    private boolean administrator(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
