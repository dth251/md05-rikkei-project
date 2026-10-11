package com.app.order;

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
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "ORDER_SAGA_LIVE_TEST", matches = "true")
class OrderSagaLiveTests {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final String gateway = System.getenv().getOrDefault("ORDER_LIVE_URL", "http://127.0.0.1:8080");

    @Test
    void paymentSuccessConfirmsAndConsumesReservedStockOnce() throws Exception {
        scenario("SUCCESS", "CONFIRMED", 0);
    }

    @Test
    void paymentFailureCancelsAndReturnsStockOnce() throws Exception {
        scenario("FAILED", "CANCELLED", 1);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SAGA_RECOVERY_TEST", matches = "true")
    void stoppedPaymentLeavesPendingOrderAndRecoversFromDurableQueue() throws Exception {
        JsonNode order;
        try {
            docker("stop", "payment-service");
            order = createOrder();
            assertEquals("PENDING_PAYMENT", getOrder(order.path("id").asLong()).path("status").asText());
            assertEquals(503, send("GET", "/api/payments/by-order/" + order.path("id").asLong(), null, "USER").statusCode());
        } finally {
            docker("up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "payment-service");
        }
        JsonNode payment = awaitPayment(order.path("id").asLong());
        assertEquals(200, send("POST", "/api/payments/" + payment.path("id").asLong() + "/simulate",
                Map.of("status", "SUCCESS"), "ADMIN").statusCode());
        awaitOrder(order.path("id").asLong(), "CONFIRMED");
        stock(order, 0, 0);
    }

    private void scenario(
            String outcome,
            String expected,
            int available
    ) throws Exception {
        JsonNode order = createOrder();
        long id = order.path("id").asLong();
        JsonNode payment = awaitPayment(id);
        assertEquals(200, send("POST", "/api/payments/" + payment.path("id").asLong() + "/simulate",
                Map.of("status", outcome), "ADMIN").statusCode());
        awaitOrder(id, expected);
        stock(order, available, 0);
        String eventId = UUID.randomUUID().toString();
        publishOutcome(eventId, order, payment, outcome);
        publishOutcome(eventId, order, payment, outcome);
        String lateId = UUID.randomUUID().toString();
        publishOutcome(lateId, order, payment, outcome.equals("SUCCESS") ? "FAILED" : "SUCCESS");
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", eventId, lateId) != 2) {
            Thread.sleep(300);
        }
        assertEquals(2, scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", eventId, lateId));
        assertEquals(expected, getOrder(id).path("status").asText());
        stock(order, available, 0);
        assertEquals(1, scalar("SELECT count(*) FROM outbox_events WHERE event_type IN ('order.confirmed', 'order.cancelled') AND payload LIKE ?",
                "%\"orderId\":" + id + ",%"));
    }

    private JsonNode createOrder() throws Exception {
        var product = send("POST", "/api/products", Map.of("sku", "SAGA-" + UUID.randomUUID(),
                "name", "Saga fixture", "unitPrice", 12.50), "ADMIN");
        assertEquals(201, product.statusCode());
        long productId = json.readTree(product.body()).path("data").path("id").asLong();
        assertEquals(200, send("POST", "/api/inventory/stocks", Map.of("productId", productId, "quantity", 1), "ADMIN").statusCode());
        var response = send("POST", "/api/orders", Map.of("channel", "POS", "idempotencyKey", UUID.randomUUID().toString(),
                "items", List.of(Map.of("productId", productId, "quantity", 1))), "USER");
        assertEquals(201, response.statusCode());
        JsonNode order = json.readTree(response.body()).path("data");
        assertEquals("PENDING_PAYMENT", order.path("status").asText());
        stock(order, 0, 1);
        return order;
    }

    private JsonNode awaitPayment(long orderId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/payments/by-order/" + orderId, null, "USER");
            if (response.statusCode() == 200) {
                JsonNode payment = json.readTree(response.body()).path("data");
                assertEquals("PENDING", payment.path("status").asText());
                return payment;
            }
            assertEquals(404, response.statusCode());
            Thread.sleep(300);
        }
        throw new AssertionError("Payment did not consume order.created");
    }

    private void awaitOrder(
            long orderId,
            String status
    ) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            if (status.equals(getOrder(orderId).path("status").asText())) {
                return;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Order did not apply payment outcome");
    }

    private JsonNode getOrder(long orderId) throws Exception {
        var response = send("GET", "/api/orders/" + orderId, null, "USER");
        assertEquals(200, response.statusCode());
        assertEquals("api-gateway", response.headers().firstValue("X-Gateway").orElseThrow());
        return json.readTree(response.body()).path("data");
    }

    private void stock(
            JsonNode order,
            int available,
            int reserved
    ) throws Exception {
        var response = send("GET", "/api/inventory/" + order.path("items").get(0).path("productId").asLong(), null, "USER");
        assertEquals(200, response.statusCode());
        JsonNode data = json.readTree(response.body()).path("data");
        assertEquals(available, data.path("available").asInt());
        assertEquals(reserved, data.path("reserved").asInt());
    }

    private void publishOutcome(
            String eventId,
            JsonNode order,
            JsonNode payment,
            String outcome
    ) throws Exception {
        String type = outcome.equals("SUCCESS") ? "payment.succeeded" : "payment.failed";
        var event = Map.of("eventId", eventId, "type", type, "orderId", order.path("id").asLong(),
                "userId", order.path("userId").asLong(), "amount", order.path("totalAmount").decimalValue(),
                "paymentId", payment.path("id").asLong(), "occurredAt", Instant.now().toString());
        String user = System.getenv().getOrDefault("RABBITMQ_LIVE_USER", "commerce_app");
        String password = System.getenv("RABBITMQ_LIVE_PASSWORD");
        assertTrue(password != null && !password.isBlank());
        String authorization = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String base = System.getenv().getOrDefault("RABBITMQ_LIVE_URL", "http://127.0.0.1:15672");
        var request = HttpRequest.newBuilder(URI.create(base + "/api/exchanges/%2F/commerce.events/publish"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", authorization).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("routing_key", type,
                        "payload", json.writeValueAsString(event), "payload_encoding", "string",
                        "properties", Map.of("delivery_mode", 2, "content_type", "application/json"))))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(json.readTree(response.body()).path("routed").asBoolean());
    }

    private int scalar(
            String sql,
            Object... arguments
    ) throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv().getOrDefault("ORDER_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/order_db"), "order_user", System.getenv("ORDER_LIVE_DB_PASSWORD"));
             var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private HttpResponse<String> send(
            String method,
            String path,
            Object body,
            String role
    ) throws Exception {
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(System.getenv("JWT_SECRET")));
        String token = Jwts.builder().subject("900201").issuer("omnichannel-auth").issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600))).claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
        var request = HttpRequest.newBuilder(URI.create(gateway + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker", "compose"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).directory(Path.of("..").toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(210, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Docker recovery timed out");
        }
        assertEquals(0, process.exitValue(), "Docker recovery operation failed");
    }
}
