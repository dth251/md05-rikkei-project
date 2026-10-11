package com.app.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.app.gateway.config.RateLimitKeyResolver;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimitKeyResolverTests {

    @Test
    void untrustedPeerCannotChooseRateLimitIdentity() {
        var resolver = new RateLimitKeyResolver("127.0.0.1");
        var request = MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("198.51.100.1", 1000))
                .header("X-Real-IP", "203.0.113.5").build();
        assertEquals("198.51.100.1", resolver.clientIp(MockServerWebExchange.from(request)));
    }

    @Test
    void configuredProxyMaySupplyClientIp() {
        var resolver = new RateLimitKeyResolver("127.0.0.1");
        var request = MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 1000))
                .header("X-Real-IP", "203.0.113.5").build();
        assertEquals("203.0.113.5", resolver.clientIp(MockServerWebExchange.from(request)));
    }
}
