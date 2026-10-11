package com.app.gateway.web;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final GatewayResponses responses;

    public GatewayErrorHandler(
            GatewayResponses responses
    ) {
        this.responses = responses;
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            Throwable exception
    ) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        if (exception instanceof ResponseStatusException responseStatus) {
            status = HttpStatus.valueOf(responseStatus.getStatusCode().value());
        }
        return responses.error(exchange, status, status == HttpStatus.NOT_FOUND
                ? "Route not found" : "Upstream temporarily unavailable");
    }
}
