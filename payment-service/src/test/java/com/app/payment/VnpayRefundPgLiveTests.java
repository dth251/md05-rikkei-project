package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.app.payment.service.VnpayRefundTransactionService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Repeat the provider fixture suite on real PostgreSQL, then verify a durable claim through a restarted app. */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "VNPAY_LIVE_TEST", matches = "true")
class VnpayRefundPgLiveTests extends VnpayRefundTests {

    @Autowired
    private VnpayRefundTransactionService transactions;

    @DynamicPropertySource
    static void postgres(
            DynamicPropertyRegistry properties
    ) {
        properties.add("spring.datasource.url", () -> System.getenv().getOrDefault("PAYMENT_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/payment_db"));
        properties.add("spring.datasource.username", () -> "payment_user");
        properties.add("spring.datasource.password", () -> System.getenv("PAYMENT_LIVE_DB_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "VNPAY_RECOVERY_TEST", matches = "true")
    void restartedPaymentDoesNotResubmitClaimCommittedBeforeProviderCall() throws Exception {
        var payment = lateCapture();
        var claim = transactions.prepareRefund(payment.getId(), 25L);
        assertTrue(claim.send());
        assertEquals(0, REQUESTS.get());
        var command = List.of("docker", "compose", "restart", "payment-service");
        var process = new ProcessBuilder(command).directory(Path.of(
                System.getenv().getOrDefault("PAYMENT_SMOKE_PROJECT_DIR", "..")).toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(150, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Payment restart timed out");
        }
        assertEquals(0, process.exitValue(), "Payment restart failed");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        Instant deadline = Instant.now().plusSeconds(150);
        boolean ready = false;
        String health = System.getenv().getOrDefault("PAYMENT_HEALTH_URL", "http://127.0.0.1:8085/actuator/health");
        while (Instant.now().isBefore(deadline)) {
            try {
                var response = client.send(HttpRequest.newBuilder(URI.create(health)).timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    ready = true;
                    break;
                }
            } catch (java.io.IOException exception) {
                // Bounded recovery polling; no credentials or provider payload in diagnostics.
            }
            Thread.sleep(500);
        }
        assertTrue(ready, "Payment did not recover");
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(System.getenv("JWT_SECRET")));
        String token = Jwts.builder().subject("25").issuer("omnichannel-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(5))).expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("roles", List.of("ADMIN")).signWith(key).compact();
        String gateway = System.getenv().getOrDefault("PAYMENT_LIVE_URL", "http://127.0.0.1:8080");
        var response = client.send(HttpRequest.newBuilder(URI.create(gateway + "/api/payments/" + payment.getId() + "/vnpay/refund"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).path("data");
        assertEquals(claim.refundRequestId(), data.path("requestId").asText());
        assertEquals("UNKNOWN", data.path("state").asText());
        assertEquals(0, REQUESTS.get());
    }
}
