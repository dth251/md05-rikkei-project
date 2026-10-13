package com.app.gateway.config;

import com.app.gateway.web.GatewayResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
public class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            GatewayResponses responses
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/payments/vnpay/ipn", "/api/payments/vnpay/return").permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, exception) ->
                                responses.error(exchange, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((exchange, exception) ->
                                responses.error(exchange, HttpStatus.FORBIDDEN, "Access denied"))
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> {})
                        .authenticationEntryPoint((exchange, exception) ->
                                responses.error(exchange, HttpStatus.UNAUTHORIZED, "Invalid bearer token"))
                )
                .build();
    }
}
