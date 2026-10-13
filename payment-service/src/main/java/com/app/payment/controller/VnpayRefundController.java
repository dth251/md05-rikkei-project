package com.app.payment.controller;

import com.app.payment.dto.response.ApiResponse;
import com.app.payment.dto.response.VnpayRefundResponse;
import com.app.payment.service.VnpayRefundService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VnpayRefundController {

    private final VnpayRefundService refunds;

    public VnpayRefundController(VnpayRefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping("/api/payments/{id}/vnpay/refund")
    public ResponseEntity<ApiResponse<VnpayRefundResponse>> refund(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(refunds.submit(id, Long.valueOf(authentication.getName())),
                "VNPay refund status recorded"));
    }

    @PostMapping("/api/payments/{id}/vnpay/reconcile")
    public ResponseEntity<ApiResponse<VnpayRefundResponse>> reconcile(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(refunds.reconcile(id), "VNPay refund reconciliation recorded"));
    }
}
