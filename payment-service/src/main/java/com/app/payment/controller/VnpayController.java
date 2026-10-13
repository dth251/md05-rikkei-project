package com.app.payment.controller;

import com.app.payment.dto.response.ApiResponse;
import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.dto.response.VnpayIpnResponse;
import com.app.payment.dto.response.VnpayReturnResponse;
import com.app.payment.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VnpayController {

    private final VnpayService service;

    public VnpayController(VnpayService service) {
        this.service = service;
    }

    @PostMapping("/api/payments/{id}/vnpay/checkout")
    public ResponseEntity<ApiResponse<VnpayCheckoutResponse>> checkout(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        String forwarded = request.getHeader("X-Commerce-Client-IP");
        return ResponseEntity.ok(ApiResponse.success(service.checkout(id, Long.valueOf(authentication.getName()),
                forwarded == null ? request.getRemoteAddr() : forwarded), "VNPay sandbox checkout created"));
    }

    @GetMapping("/api/payments/vnpay/ipn")
    public ResponseEntity<VnpayIpnResponse> ipn(@RequestParam MultiValueMap<String, String> parameters) {
        // VNPay requires its protocol response, rather than the application's ApiResponse envelope.
        return ResponseEntity.ok(service.ipn(parameters));
    }

    @GetMapping("/api/payments/vnpay/return")
    public ResponseEntity<ApiResponse<VnpayReturnResponse>> returnResult(
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.verifyReturn(parameters), "VNPay return validated"));
    }
}
