package com.app.payment.controller;

import com.app.payment.dto.response.ApiResponse;
import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.service.ReturnRefundService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulatedRefundController {

    private final ReturnRefundService refunds;

    public SimulatedRefundController(ReturnRefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping("/api/payments/{id}/simulate-refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> simulateRefund(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(refunds.simulateRefund(id), "Simulated refund recorded"));
    }
}
