package com.app.gateway.web;

import com.app.gateway.config.RateLimitKeyResolver;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class PaymentClientAddressFilter implements GlobalFilter, Ordered {

    private final RateLimitKeyResolver addresses;

    public PaymentClientAddressFilter(RateLimitKeyResolver addresses) {
        this.addresses = addresses;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (exchange.getRequest().getMethod() == HttpMethod.POST
                && path.matches("/api/payments/[1-9][0-9]*/vnpay/checkout")) {
            var request = exchange.getRequest().mutate()
                    .headers(headers -> headers.set("X-Commerce-Client-IP", addresses.clientIp(exchange)))
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
