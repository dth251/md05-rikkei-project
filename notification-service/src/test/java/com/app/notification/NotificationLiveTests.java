package com.app.notification;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
class NotificationLiveTests {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final String gateway = System.getenv().getOrDefault("NOTIFICATION_LIVE_URL", "http://127.0.0.1:8080");

    @Test
    void shipmentAndCompletionHaveOwnLogsAndReplayDoesNotDuplicateStages() throws Exception {
        var fixture = scenario("SUCCESS", "CONFIRMED", "order.confirmed", "WEB");
        long orderId = fixture.order().path("id").asLong();
        var shipment = Map.of("carrier", "Fixture Carrier", "trackingNumber", "TRACK-" + UUID.randomUUID(),
                "shippingAddress", Map.of("recipientName", "Fixture Recipient", "phone", "0901234567",
                        "street", "Fixture street", "ward", "Ward", "district", "District", "province", "Province"));
        assertEquals(409, send("POST", "/api/orders/" + orderId + "/complete", null, adminToken()).statusCode());
        replayConcurrently("/api/orders/" + orderId + "/ship", shipment);
        var shipped = send("POST", "/api/orders/" + orderId + "/ship", shipment, adminToken());
        assertEquals(200, shipped.statusCode());
        assertEquals("SHIPPING", json.readTree(shipped.body()).path("data").path("status").asText());
        // Separate the two bursts within the Gateway's configured 20/s, burst-40 quota.
        Thread.sleep(1100);
        replayConcurrently("/api/orders/" + orderId + "/complete", null);
        var completed = send("POST", "/api/orders/" + orderId + "/complete", null, adminToken());
        assertEquals(200, completed.statusCode());
        assertEquals("COMPLETED", json.readTree(completed.body()).path("data").path("status").asText());
        assertTrue(!json.readTree(completed.body()).path("data").path("shipment").path("completedAt").isNull());
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM fulfillment_logs WHERE order_id=?", orderId) != 2) {
            Thread.sleep(300);
        }
        assertEquals(2, scalar("SELECT count(*) FROM fulfillment_logs WHERE order_id=?", orderId));
        assertEquals(1, scalar("SELECT count(*) FROM notification_logs WHERE order_id=?", orderId));
        String replayId = UUID.randomUUID().toString();
        publishFulfillment(replayId, fixture, "order.shipped");
        publishFulfillment(replayId, fixture, "order.shipped");
        String completedId = UUID.randomUUID().toString();
        publishFulfillment(completedId, fixture, "order.completed");
        deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)
                && scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?,?)", replayId, completedId) != 2) {
            Thread.sleep(300);
        }
        assertEquals(2, scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?,?)", replayId, completedId));
        assertEquals(2, scalar("SELECT count(*) FROM fulfillment_logs WHERE order_id=?", orderId));
    }

    private void replayConcurrently(
            String path,
            Object body
    ) throws Exception {
        String admin = adminToken();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 20; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return send("POST", path, body, admin).statusCode();
                }));
            }
            start.countDown();
            for (var result : results) {
                assertEquals(200, result.get());
            }
        }
    }

    private void publishFulfillment(
            String eventId,
            Fixture fixture,
            String type
    ) throws Exception {
        var event = Map.of("eventId", eventId, "type", type, "orderId", fixture.order().path("id").asLong(),
                "userId", fixture.order().path("userId").asLong(), "amount", fixture.order().path("totalAmount").decimalValue(),
                "occurredAt", Instant.now().toString());
        String user = System.getenv().getOrDefault("RABBITMQ_LIVE_USER", "commerce_app");
        String password = System.getenv("RABBITMQ_LIVE_PASSWORD");
        assertTrue(password != null && !password.isBlank());
        String credentials = Base64.getEncoder().encodeToString((user + ":" + password)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var body = Map.of("properties", Map.of("delivery_mode", 2, "content_type", "application/json", "message_id", eventId),
                "routing_key", type, "payload", json.writeValueAsString(event), "payload_encoding", "string");
        var request = HttpRequest.newBuilder(URI.create(System.getenv().getOrDefault("RABBITMQ_LIVE_URL", "http://127.0.0.1:15672")
                        + "/api/exchanges/%2F/commerce.events/publish"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .header("Authorization", "Basic " + credentials)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(json.readTree(response.body()).path("routed").asBoolean());
    }

    @Test
    void confirmedOrderWithRealAuthTokenLogsNotification() throws Exception {
        scenario("SUCCESS", "CONFIRMED", "order.confirmed", "WEB");
    }

    @Test
    void cancelledPosOrderLogsNotificationAndReturnsStock() throws Exception {
        scenario("FAILED", "CANCELLED", "order.cancelled", "POS");
    }

    @Test
    void duplicateEventAndDifferentIdForSameOrderLogOnlyOnce() throws Exception {
        var fixture = scenario("SUCCESS", "CONFIRMED", "order.confirmed", "WEB");
        String eventId = UUID.randomUUID().toString();
        publish(eventId, fixture);
        publish(eventId, fixture);
        String anotherId = UUID.randomUUID().toString();
        publish(anotherId, fixture);
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", eventId, anotherId) != 2) {
            Thread.sleep(300);
        }
        assertEquals(2, scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", eventId, anotherId));
        assertEquals(1, scalar("SELECT count(*) FROM notification_logs WHERE order_id=?", fixture.order().path("id").asLong()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "NOTIFICATION_RECOVERY_TEST", matches = "true")
    void stoppedNotificationDoesNotBlockSagaAndConsumesQueueOnRestart() throws Exception {
        Fixture fixture;
        try {
            docker("stop", "notification-service");
            fixture = finalized("SUCCESS", "CONFIRMED", "WEB");
            assertEquals(0, scalar("SELECT count(*) FROM notification_logs WHERE order_id=?", fixture.order().path("id").asLong()));
        } finally {
            docker("up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "notification-service");
        }
        awaitNotification(fixture.order().path("id").asLong(), "order.confirmed");
    }

    private Fixture scenario(
            String outcome,
            String status,
            String eventType,
            String channel
    ) throws Exception {
        Fixture fixture = finalized(outcome, status, channel);
        awaitNotification(fixture.order().path("id").asLong(), eventType);
        return fixture;
    }

    private Fixture finalized(
            String outcome,
            String expected,
            String channel
    ) throws Exception {
        String email = "day2-e2e-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString();
        assertEquals(201, send("POST", "/api/auth/register", Map.of("fullName", "Day Two E2E",
                "email", email, "password", password), null).statusCode());
        HttpResponse<String> login = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            login = send("POST", "/api/auth/login", Map.of("email", email, "password", password), null);
            if (login.statusCode() != 429) {
                break;
            }
            Thread.sleep(700);
        }
        assertEquals(200, login.statusCode());
        String userToken = json.readTree(login.body()).path("data").path("accessToken").asText();
        assertTrue(!userToken.isBlank());
        assertEquals(200, send("GET", "/api/auth/me", null, userToken).statusCode());
        var product = send("POST", "/api/products", Map.of("sku", "E2E-" + UUID.randomUUID(),
                "name", "Notification fixture", "unitPrice", 12.50), adminToken());
        assertEquals(201, product.statusCode());
        long productId = json.readTree(product.body()).path("data").path("id").asLong();
        assertEquals(200, send("POST", "/api/inventory/stocks", Map.of("productId", productId, "quantity", 1, "idempotencyKey", UUID.randomUUID().toString()), adminToken()).statusCode());
        var created = send("POST", "/api/orders", Map.of("channel", channel, "idempotencyKey", UUID.randomUUID().toString(),
                "items", List.of(Map.of("productId", productId, "quantity", 1))), userToken);
        assertEquals(201, created.statusCode());
        JsonNode order = json.readTree(created.body()).path("data");
        long orderId = order.path("id").asLong();
        JsonNode payment = awaitPayment(orderId, userToken);
        assertEquals(200, send("POST", "/api/payments/" + payment.path("id").asLong() + "/simulate",
                Map.of("status", outcome), adminToken()).statusCode());
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/orders/" + orderId, null, userToken);
            assertEquals(200, response.statusCode());
            assertEquals("api-gateway", response.headers().firstValue("X-Gateway").orElseThrow());
            if (expected.equals(json.readTree(response.body()).path("data").path("status").asText())) {
                var inventory = send("GET", "/api/inventory/" + productId, null, userToken);
                assertEquals(200, inventory.statusCode());
                JsonNode stock = json.readTree(inventory.body()).path("data");
                assertEquals(outcome.equals("FAILED") ? 1 : 0, stock.path("available").asInt());
                assertEquals(0, stock.path("reserved").asInt());
                return new Fixture(order, payment);
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Saga did not finalize order through Gateway");
    }

    private JsonNode awaitPayment(
            long orderId,
            String token
    ) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/payments/by-order/" + orderId, null, token);
            if (response.statusCode() == 200) {
                return json.readTree(response.body()).path("data");
            }
            assertEquals(404, response.statusCode());
            Thread.sleep(300);
        }
        throw new AssertionError("Payment did not consume order");
    }

    private void awaitNotification(
            long orderId,
            String type
    ) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM notification_logs WHERE order_id=? AND event_type=?", orderId, type) != 1) {
            Thread.sleep(300);
        }
        assertEquals(1, scalar("SELECT count(*) FROM notification_logs WHERE order_id=? AND event_type=?", orderId, type));
    }

    private void publish(
            String eventId,
            Fixture fixture
    ) throws Exception {
        var event = Map.of("eventId", eventId, "type", "order.confirmed", "orderId", fixture.order().path("id").asLong(),
                "userId", fixture.order().path("userId").asLong(), "amount", fixture.order().path("totalAmount").decimalValue(),
                "paymentId", fixture.payment().path("id").asLong(), "occurredAt", Instant.now().toString());
        String user = System.getenv().getOrDefault("RABBITMQ_LIVE_USER", "commerce_app");
        String password = System.getenv("RABBITMQ_LIVE_PASSWORD");
        assertTrue(password != null && !password.isBlank());
        String authorization = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String base = System.getenv().getOrDefault("RABBITMQ_LIVE_URL", "http://127.0.0.1:15672");
        var request = HttpRequest.newBuilder(URI.create(base + "/api/exchanges/%2F/commerce.events/publish"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", authorization).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("routing_key", "order.confirmed",
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
        try (var connection = DriverManager.getConnection(System.getenv().getOrDefault("NOTIFICATION_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/notification_db"), "notification_user", System.getenv("NOTIFICATION_LIVE_DB_PASSWORD"));
             var statement = connection.prepareStatement(sql)) {
            assertEquals("notification_db", connection.getCatalog());
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private String adminToken() {
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(System.getenv("JWT_SECRET")));
        // The test client and VPS can differ slightly in time; the API rejects future iat.
        return Jwts.builder().subject("999333").issuer("omnichannel-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(600))).claim("roles", List.of("ADMIN"))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    private HttpResponse<String> send(
            String method,
            String path,
            Object body,
            String token
    ) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(gateway + path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker", "compose"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).directory(Path.of(
                System.getenv().getOrDefault("NOTIFICATION_SMOKE_PROJECT_DIR", "..")).toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(210, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Docker recovery timed out");
        }
        assertEquals(0, process.exitValue(), "Docker recovery operation failed");
    }

    private record Fixture(
            JsonNode order,
            JsonNode payment
    ) {
    }
}
