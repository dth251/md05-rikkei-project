package com.app.payment.controller;

import com.app.payment.dto.request.CashRefundRequest;
import com.app.payment.dto.response.ApiResponse;
import com.app.payment.dto.response.CashRefundResponse;
import com.app.payment.service.ReturnRefundService;
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
@RequestMapping("/api/payments/{id}/cod/refund")
public class CashRefundController {

    private final ReturnRefundService refunds;

    public CashRefundController(ReturnRefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CashRefundResponse>> refund(
            @PathVariable Long id,
            @Valid @RequestBody CashRefundRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(refunds.refundCash(id,
                Long.valueOf(authentication.getName()), request), "Cash refund recorded"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CashRefundResponse>> get(
            @PathVariable Long id,
            Authentication authentication
    ) {
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.success(refunds.getCashRefund(id,
                Long.valueOf(authentication.getName()), administrator), "Cash refund retrieved"));
    }
}
