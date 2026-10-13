package com.app.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureWebTestClient
class GatewayVnpayTests {

    private static final HttpServer BACKEND = startBackend();
    @Autowired private WebTestClient client;
    @MockitoBean private ReactiveStringRedisTemplate redis;
    @MockitoBean private RedisRateLimiter limiter;

    @DynamicPropertySource
    static void paymentBackend(DynamicPropertyRegistry properties) {
        properties.add("PAYMENT_URI", () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.stop(0);
    }

    @Test
    void callbacksAreAnonymousOnlyForGetAndCheckoutRequiresJwt() {
        available();
        client.get().uri("/api/payments/vnpay/ipn?vnp_Amount=1250").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.RspCode").isEqualTo("97");
        client.get().uri("/api/payments/vnpay/return").exchange().expectStatus().isOk();
        client.post().uri("/api/payments/vnpay/ipn").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/payments/1").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/payments/1/vnpay/checkout").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void checkoutOverwritesExternalClientAddressHeader() throws Exception {
        available();
        client.post().uri("/api/payments/1/vnpay/checkout").header("Authorization", token())
                .header("X-Commerce-Client-IP", "192.0.2.66").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.clientIp").value(value ->
                        org.junit.jupiter.api.Assertions.assertNotEquals("192.0.2.66", value));
    }

    @Test
    void redisFailureStopsAnonymousCallbackInsteadOfBypassingQuota() {
        when(redis.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("Fixture outage")));
        client.get().uri("/api/payments/vnpay/ipn").exchange().expectStatus().isEqualTo(503);
    }

    private void available() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        when(limiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(true, java.util.Map.of())));
    }

    private String token() throws Exception {
        var claims = new JWTClaimsSet.Builder().subject("10").issuer("omnichannel-auth")
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .claim("roles", List.of("USER")).build();
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(new byte[32]));
        return "Bearer " + token.serialize();
    }

    private static HttpServer startBackend() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String address = exchange.getRequestHeaders().getFirst("X-Commerce-Client-IP");
                String body = "{\"RspCode\":\"97\",\"clientIp\":\"" + (address == null ? "absent" : address) + "\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
            return server;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Fixture backend unavailable", exception);
        }
    }
}
