package com.app.gateway.web;

import java.time.Duration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-50)
public class RateLimitAvailabilityFilter implements WebFilter {

    private final ReactiveStringRedisTemplate redis;
    private final GatewayResponses responses;

    public RateLimitAvailabilityFilter(
            ReactiveStringRedisTemplate redis,
            GatewayResponses responses
    ) {
        this.redis = redis;
        this.responses = responses;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain
    ) {
        String path = exchange.getRequest().getPath().value();
        boolean limited = (("/api/auth/login".equals(path) || "/api/auth/refresh".equals(path)
                || "/api/auth/logout".equals(path))
                && exchange.getRequest().getMethod() == HttpMethod.POST)
                || path.equals("/api/orders") || path.startsWith("/api/orders/")
                || path.startsWith("/api/payments/vnpay/")
                || path.matches("/api/payments/[1-9][0-9]*/vnpay/checkout");
        if (!limited) {
            return chain.filter(exchange);
        }
        return redis.hasKey("gateway:availability-probe")
                .timeout(Duration.ofSeconds(2))
                .map(ignored -> true)
                .onErrorReturn(false)
                .flatMap(available -> available ? chain.filter(exchange)
                        : responses.error(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                                "Rate limiting temporarily unavailable"));
    }
}
