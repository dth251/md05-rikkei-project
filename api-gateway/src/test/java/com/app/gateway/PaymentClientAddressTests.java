package com.app.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.app.gateway.config.RateLimitKeyResolver;
import com.app.gateway.web.PaymentClientAddressFilter;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class PaymentClientAddressTests {

    @Test
    void checkoutOverwritesSpoofedHeaderUsingDirectPeer() {
        var request = MockServerHttpRequest.post("/api/payments/1/vnpay/checkout")
                .remoteAddress(new InetSocketAddress("203.0.113.10", 12345))
                .header("X-Commerce-Client-IP", "192.0.2.66")
                .header("X-Real-IP", "192.0.2.77").build();
        var actual = forward(MockServerWebExchange.from(request));
        assertEquals("203.0.113.10", actual.getRequest().getHeaders().getFirst("X-Commerce-Client-IP"));
    }

    @Test
    void trustedNginxUsesValidatedRealIp() {
        var request = MockServerHttpRequest.post("/api/payments/1/vnpay/checkout")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Commerce-Client-IP", "192.0.2.66")
                .header("X-Real-IP", "203.0.113.10").build();
        var actual = forward(MockServerWebExchange.from(request));
        assertEquals("203.0.113.10", actual.getRequest().getHeaders().getFirst("X-Commerce-Client-IP"));
    }

    private ServerWebExchange forward(ServerWebExchange exchange) {
        var forwarded = new AtomicReference<ServerWebExchange>();
        var filter = new PaymentClientAddressFilter(new RateLimitKeyResolver("127.0.0.1,::1"));
        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();
        return forwarded.get();
    }
}
