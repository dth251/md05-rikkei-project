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
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class GatewaySecurityTests {

    private static final HttpServer backend = startBackend();

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ReactiveStringRedisTemplate redis;

    @MockitoBean
    private RedisRateLimiter limiter;

    @DynamicPropertySource
    static void backendProperties(
            DynamicPropertyRegistry properties
    ) {
        String uri = "http://127.0.0.1:" + backend.getAddress().getPort();
        properties.add("AUTH_URI", () -> uri);
        properties.add("PRODUCT_URI", () -> uri);
        properties.add("ORDER_URI", () -> uri);
        properties.add("PAYMENT_URI", () -> "http://127.0.0.1:1");
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
    }

    @Test
    void privatePrometheusExportsJvmMetrics() {
        client.mutate().responseTimeout(java.time.Duration.ofSeconds(30)).build()
                .get().uri("/actuator/prometheus").exchange().expectStatus().isOk()
                .expectBody(String.class).value(body ->
                        org.junit.jupiter.api.Assertions.assertTrue(body.contains("jvm_memory_used_bytes")));
    }

    @Test
    void publicProductPreservesPathAndRemovesSpoofedIdentity() {
        client.get().uri("/api/products/42").header("X-User-Id", "admin")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Gateway", "api-gateway")
                .expectHeader().exists("X-Request-Id")
                .expectBody().jsonPath("$.path").isEqualTo("/api/products/42")
                .jsonPath("$.identity").isEqualTo("absent");
    }

    @Test
    void publicRegistrationDoesNotNeedJwt() {
        client.post().uri("/api/auth/register").exchange().expectStatus().isOk();
    }

    @Test
    void refreshAndLogoutReachAuthWithoutAccessJwtWhileAddressesRemainPrivate() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        when(limiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(true, java.util.Map.of())));
        client.post().uri("/api/auth/refresh").exchange().expectStatus().isOk();
        client.post().uri("/api/auth/logout").exchange().expectStatus().isOk();
        client.get().uri("/api/auth/addresses").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void sessionEndpointsFailClosedWhenRedisIsUnavailable() {
        when(redis.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("unavailable")));
        client.post().uri("/api/auth/refresh").exchange().expectStatus().isEqualTo(503);
        client.post().uri("/api/auth/logout").exchange().expectStatus().isEqualTo(503);
    }

    @Test
    void protectedRouteRequiresJwt() {
        client.get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validAuthCompatibleJwtReachesBackend() throws Exception {
        client.get().uri("/api/auth/me").headers(headers -> headers.setBearerAuth(token(
                        "omnichannel-auth", Instant.now().plusSeconds(60), "1", List.of("USER"), new byte[32])))
                .exchange().expectStatus().isOk();
    }

    @Test
    void expiredWrongIssuerSignatureAndClaimsAreRejected() throws Exception {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        List<String> invalid = List.of(
                token("wrong", Instant.now().plusSeconds(60), "1", List.of("USER"), new byte[32]),
                token("omnichannel-auth", Instant.now().minusSeconds(1), "1", List.of("USER"), new byte[32]),
                token("omnichannel-auth", Instant.now().plusSeconds(60), "1", List.of("USER"), otherKey),
                token("omnichannel-auth", Instant.now().plusSeconds(60), "-1", List.of("USER"), new byte[32]),
                token("omnichannel-auth", Instant.now().plusSeconds(60), "1", List.of("ROOT"), new byte[32])
        );
        for (String value : invalid) {
            client.get().uri("/api/auth/me").headers(headers -> headers.setBearerAuth(value))
                    .exchange().expectStatus().isUnauthorized();
        }
    }

    @Test
    void missingExpiryIsRejected() throws Exception {
        client.get().uri("/api/auth/me").headers(headers -> headers.setBearerAuth(token(
                        "omnichannel-auth", null, "1", List.of("USER"), new byte[32])))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void redisFailureRejectsLoginInsteadOfBypassingRateLimit() {
        when(redis.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("unavailable")));
        client.post().uri("/api/auth/login").exchange().expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message").isEqualTo("Rate limiting temporarily unavailable");
        client.get().uri("/api/products").exchange().expectStatus().isOk();
    }

    @Test
    void quotaExceededReturns429() {
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        when(limiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RateLimiter.Response(false, java.util.Map.of())));
        client.post().uri("/api/auth/login").exchange().expectStatus().isEqualTo(429);
    }

    @Test
    void deadBackendReturns503() throws Exception {
        client.get().uri("/api/payments/1").headers(headers -> headers.setBearerAuth(token(
                        "omnichannel-auth", Instant.now().plusSeconds(60), "1", List.of("USER"), new byte[32])))
                .exchange().expectStatus().isEqualTo(503);
    }

    @Test
    void healthIsMinimalAndOtherActuatorEndpointsAreProtected() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
    }

    private static String token(
            String issuer,
            Instant expires,
            String subject,
            List<String> roles,
            byte[] key
    ) {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject)
                .issueTime(Date.from(Instant.now().minusSeconds(5))).claim("roles", roles);
        if (expires != null) {
            claims.expirationTime(Date.from(expires));
        }
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        try {
            jwt.sign(new MACSigner(key));
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException(exception);
        }
        return jwt.serialize();
    }

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String identity = exchange.getRequestHeaders().getFirst("X-User-Id");
                byte[] bytes = ("{\"path\":\"" + exchange.getRequestURI().getPath()
                        + "\",\"identity\":\"" + (identity == null ? "absent" : identity) + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
