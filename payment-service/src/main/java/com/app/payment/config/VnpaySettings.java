package com.app.payment.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VnpaySettings {

    private final boolean enabled;
    private final String terminal;
    private final String secret;
    private final String returnUrl;

    public VnpaySettings(
            @Value("${payment.vnpay.enabled:false}") boolean enabled,
            @Value("${payment.vnpay.tmn-code:}") String terminal,
            @Value("${payment.vnpay.hash-secret:}") String secret,
            @Value("${payment.vnpay.return-url:https://md05-rikkei-project.duckdns.org/api/payments/vnpay/return}") String returnUrl
    ) {
        this.enabled = enabled;
        this.terminal = terminal;
        this.secret = secret;
        this.returnUrl = returnUrl;
        if (enabled) {
            if (terminal == null || !terminal.matches("[A-Za-z0-9]{8}") || secret == null
                    || secret.length() < 16 || secret.length() > 256
                    || secret.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalStateException("Private VNPay sandbox configuration required");
            }
            try {
                var uri = URI.create(returnUrl);
                if (returnUrl.length() > 255 || !"https".equals(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || !"/api/payments/vnpay/return".equals(uri.getPath())) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("HTTPS VNPay return endpoint required");
            }
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String terminal() {
        return terminal;
    }

    public String secret() {
        return secret;
    }

    public String returnUrl() {
        return returnUrl;
    }

    public String paymentUrl() {
        // This integration is deliberately pinned to the requested sandbox.
        return "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    }
}
