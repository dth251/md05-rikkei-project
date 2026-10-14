package com.app.payment.controller;

import com.app.payment.dto.response.ApiResponse;
import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.dto.response.VnpayIpnResponse;
import com.app.payment.dto.response.VnpayReturnResponse;
import com.app.payment.service.VnpayService;
import com.app.payment.config.exception.ApiException;
import com.app.payment.mapper.VnpayReturnPageMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VnpayController {

    private final VnpayService service;
    private final VnpayReturnPageMapper pages;

    public VnpayController(VnpayService service, VnpayReturnPageMapper pages) {
        this.service = service;
        this.pages = pages;
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
    public ResponseEntity<?> returnResult(
            @RequestParam MultiValueMap<String, String> parameters,
            @RequestHeader(value = HttpHeaders.ACCEPT, defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept
    ) {
        boolean browser = MediaType.parseMediaTypes(accept).stream()
                .anyMatch(type -> type.getQualityValue() > 0 && type.getType().equals("text")
                        && type.getSubtype().equals("html"));
        var headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.setVary(java.util.List.of(HttpHeaders.ACCEPT));
        headers.set("Referrer-Policy", "no-referrer");
        if (!browser) {
            return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.success(service.verifyReturn(parameters), "VNPay return validated"));
        }
        String nonce = UUID.randomUUID().toString().replace("-", "");
        headers.setContentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8));
        headers.set("Content-Security-Policy", "default-src 'none'; script-src 'nonce-" + nonce
                + "'; style-src 'nonce-" + nonce + "'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
        try {
            return ResponseEntity.ok().headers(headers).body(pages.render(service.verifyReturn(parameters), nonce));
        } catch (ApiException exception) {
            return ResponseEntity.status(exception.getStatus()).headers(headers).body(pages.renderError(nonce));
        }
    }
}
