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

abstract class NotificationLiveFixture {
    protected final ObjectMapper json = new ObjectMapper();
    protected final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    protected final String gateway = System.getenv().getOrDefault("NOTIFICATION_LIVE_URL", "http://127.0.0.1:8080");
    protected Fixture scenario(
            String outcome,
            String status,
            String eventType,
            String channel
    ) throws Exception {
        Fixture fixture = finalized(outcome, status, channel);
        awaitNotification(fixture.order().path("id").asLong(), eventType);
        return fixture;
    }

    protected Fixture pending(
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
        return new Fixture(order, payment, email, userToken);
    }

    protected Fixture finalized(
            String outcome,
            String expected,
            String channel
    ) throws Exception {
        Fixture fixture = pending(channel);
        JsonNode order = fixture.order();
        JsonNode payment = fixture.payment();
        long orderId = order.path("id").asLong();
        long productId = order.path("items").get(0).path("productId").asLong();
        String userToken = fixture.userToken();
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
                return fixture;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Saga did not finalize order through Gateway");
    }

    protected JsonNode awaitPayment(
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

    protected void awaitNotification(
            long orderId,
            String type
    ) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM notification_logs WHERE order_id=? AND event_type=?", orderId, type) != 1) {
            Thread.sleep(300);
        }
        assertEquals(1, scalar("SELECT count(*) FROM notification_logs WHERE order_id=? AND event_type=?", orderId, type));
    }

    protected void publish(
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

    protected int scalar(
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

    protected String adminToken() {
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(System.getenv("JWT_SECRET")));
        // The test client and VPS can differ slightly in time; the API rejects future iat.
        return Jwts.builder().subject("999333").issuer("omnichannel-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(600))).claim("roles", List.of("ADMIN"))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    protected HttpResponse<String> send(
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

    protected void docker(String... arguments) throws Exception {
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

    protected record Fixture(
            JsonNode order,
            JsonNode payment,
            String email,
            String userToken
    ) {
    }
}
