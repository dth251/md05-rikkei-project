package com.app.payment.controller;

import com.app.payment.dto.request.CollectCodRequest;
import com.app.payment.dto.response.ApiResponse;
import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.service.CodPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/{id}/cod")
public class CodPaymentController {

    private final CodPaymentService payments;

    public CodPaymentController(CodPaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/collect")
    public ResponseEntity<ApiResponse<PaymentResponse>> collect(
            @PathVariable Long id,
            @Valid @RequestBody CollectCodRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(payments.collect(id, request,
                Long.valueOf(authentication.getName())), "COD collection recorded"));
    }
}
