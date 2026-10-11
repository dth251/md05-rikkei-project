package com.app.gateway.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayResponses {

    private final ObjectMapper mapper;

    public GatewayResponses(
            ObjectMapper mapper
    ) {
        this.mapper = mapper;
    }

    public Mono<Void> error(
            ServerWebExchange exchange,
            HttpStatus status,
            String message
    ) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(Map.of(
                    "success", false,
                    "message", message,
                    "errors", Map.of(),
                    "timestamp", Instant.now()
            ));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(bytes)
        ));
    }
}
