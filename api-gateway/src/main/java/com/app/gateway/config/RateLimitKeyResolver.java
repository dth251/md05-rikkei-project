package com.app.gateway.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitKeyResolver {

    private final Set<String> trustedProxies;

    public RateLimitKeyResolver(
            @Value("${gateway.trusted-proxies:127.0.0.1,::1}") String trustedProxies
    ) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Bean
    @Primary
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange));
    }

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(principal -> principal instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(principal -> "user:" + principal.getToken().getSubject())
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange)));
    }

    public String clientIp(
            ServerWebExchange exchange
    ) {
        var remote = exchange.getRequest().getRemoteAddress();
        String peer = remote == null ? "unknown" : remote.getAddress().getHostAddress();
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (trustedProxies.contains(peer) && forwarded != null
                && forwarded.matches("[0-9a-fA-F:.]{3,45}")) {
            return forwarded;
        }
        return peer;
    }
}
