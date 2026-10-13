package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

abstract class PaymentLiveFixture {
    protected final ObjectMapper json = new ObjectMapper();
    protected final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    protected final String gateway = System.getenv().getOrDefault("PAYMENT_LIVE_URL", "http://127.0.0.1:8080");
    protected JsonNode createPending() throws Exception {
        var product = send("POST", "/api/products", Map.of("sku", "PAY-" + UUID.randomUUID(),
                "name", "Payment fixture", "unitPrice", 12.50), token(900101, "ADMIN"));
        assertEquals(201, product.statusCode());
        long productId = json.readTree(product.body()).path("data").path("id").asLong();
        assertEquals(200, send("POST", "/api/inventory/stocks", Map.of("productId", productId, "quantity", 1, "idempotencyKey", UUID.randomUUID().toString()), token(900101, "ADMIN")).statusCode());
        var order = send("POST", "/api/orders", Map.of("channel", "WEB", "idempotencyKey", UUID.randomUUID().toString(),
                "items", List.of(Map.of("productId", productId, "quantity", 1))), token(900101, "USER"));
        assertEquals(201, order.statusCode());
        long orderId = json.readTree(order.body()).path("data").path("id").asLong();
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/payments/by-order/" + orderId, null, token(900101, "USER"));
            if (response.statusCode() == 200) {
                assertEquals("api-gateway", response.headers().firstValue("X-Gateway").orElseThrow());
                JsonNode payment = json.readTree(response.body()).path("data");
                assertEquals("PENDING", payment.path("status").asText());
                assertEquals(orderId, payment.path("orderId").asLong());
                return payment;
            }
            assertEquals(404, response.statusCode());
            Thread.sleep(300);
        }
        throw new AssertionError("Payment consumer did not process order.created");
    }

    protected void assertOutcome(
            long orderId,
            String eventType
    ) throws Exception {
        String pattern = "%\"orderId\":" + orderId + ",%";
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM outbox_events WHERE payload LIKE ? AND published_at IS NOT NULL", pattern) != 1) {
            Thread.sleep(300);
        }
        assertEquals(1, scalar("SELECT count(*) FROM outbox_events WHERE payload LIKE ?", pattern));
        assertEquals(1, scalar("SELECT count(*) FROM outbox_events WHERE payload LIKE ? AND event_type=? AND published_at IS NOT NULL", pattern, eventType));
    }

    protected void publishOrder(
            String eventId,
            JsonNode payment
    ) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("type", "order.created");
        event.put("orderId", payment.path("orderId").asLong());
        event.put("userId", payment.path("userId").asLong());
        event.put("amount", payment.path("amount").decimalValue());
        event.put("paymentId", null);
        event.put("occurredAt", Instant.now().toString());
        String user = System.getenv().getOrDefault("RABBITMQ_LIVE_USER", "commerce_app");
        String password = System.getenv("RABBITMQ_LIVE_PASSWORD");
        assertTrue(password != null && !password.isBlank());
        String authorization = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String url = System.getenv().getOrDefault("RABBITMQ_LIVE_URL", "http://127.0.0.1:15672");
        var request = HttpRequest.newBuilder(URI.create(url + "/api/exchanges/%2F/commerce.events/publish"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", authorization).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                        "routing_key", "order.created", "payload", json.writeValueAsString(event), "payload_encoding", "string",
                        "properties", Map.of("delivery_mode", 2, "content_type", "application/json")
                )))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(json.readTree(response.body()).path("routed").asBoolean());
    }

    protected int scalar(
            String sql,
            Object... arguments
    ) throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv().getOrDefault("PAYMENT_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/payment_db"), "payment_user", System.getenv("PAYMENT_LIVE_DB_PASSWORD"));
             var statement = connection.prepareStatement(sql)) {
            assertEquals("payment_db", connection.getCatalog());
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    protected HttpResponse<String> simulate(
            long id,
            String status,
            String role
    ) throws Exception {
        return send("POST", "/api/payments/" + id + "/simulate", Map.of("status", status), token(900101, role));
    }

    protected String token(
            long userId,
            String role
    ) {
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(System.getenv("JWT_SECRET")));
        return Jwts.builder().subject(Long.toString(userId)).issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }

    protected HttpResponse<String> send(
            String method,
            String path,
            Object body,
            String token
    ) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(gateway + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
