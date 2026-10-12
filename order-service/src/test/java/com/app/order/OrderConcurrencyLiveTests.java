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
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
class OrderConcurrencyLiveTests {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String gateway = System.getenv().getOrDefault("ORDER_LIVE_URL", "http://127.0.0.1:8080");
    private final String directOrder = System.getenv().getOrDefault("ORDER_SERVICE_URL", "http://127.0.0.1:8083");

    @Test
    void oneHundredRequestsCompeteForOneStockOnRealPostgres() throws Exception {
        long product = product();
        receive(product, 1);
        List<Callable<HttpResponse<String>>> tasks = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            String requestKey = "race-" + UUID.randomUUID();
            tasks.add(() -> create(directOrder, product, requestKey, "WEB"));
        }
        int created = 0;
        int conflicts = 0;
        try (var executor = Executors.newFixedThreadPool(16)) {
            for (var result : executor.invokeAll(tasks)) {
                int status = result.get().statusCode();
                if (status == 201) {
                    created++;
                } else {
                    assertEquals(409, status);
                    conflicts++;
                }
            }
        }
        assertEquals(1, created);
        assertEquals(99, conflicts);
        assertStock(product, 0, 1);
        try (var connection = database(); var statement = connection.prepareStatement(
                "SELECT count(*) FROM orders o JOIN order_items i ON i.order_id=o.id WHERE i.product_id=?")) {
            statement.setLong(1, product);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void concurrentSameKeyReturnsSameOrderAndReservesOnce() throws Exception {
        long product = product();
        receive(product, 1);
        String requestKey = "same-" + UUID.randomUUID();
        List<Callable<HttpResponse<String>>> tasks = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            tasks.add(() -> create(directOrder, product, requestKey, "POS"));
        }
        Long orderId = null;
        int created = 0;
        try (var executor = Executors.newFixedThreadPool(12)) {
            for (var result : executor.invokeAll(tasks)) {
                var response = result.get();
                assertTrue(response.statusCode() == 200 || response.statusCode() == 201);
                created += response.statusCode() == 201 ? 1 : 0;
                long id = json.readTree(response.body()).path("data").path("id").asLong();
                if (orderId != null) {
                    assertEquals(orderId.longValue(), id);
                }
                orderId = id;
            }
        }
        assertEquals(1, created);
        assertStock(product, 0, 1);
        assertEquals(200, create(gateway, product, requestKey, "POS").statusCode());
        assertEquals(409, create(gateway, product, requestKey, "WEB").statusCode());
        assertEquals(404, send(gateway, "GET", "/api/orders/" + orderId, null, token(900002, "USER")).statusCode());
        assertEquals(200, send(gateway, "GET", "/api/orders/" + orderId, null, token(900002, "ADMIN")).statusCode());
    }

    private long product() throws Exception {
        var response = send(gateway, "POST", "/api/products", Map.of(
                "sku", "ORD-" + UUID.randomUUID(), "name", "Order race fixture", "unitPrice", 12.50
        ), token(900001, "ADMIN"));
        assertEquals(201, response.statusCode());
        return json.readTree(response.body()).path("data").path("id").asLong();
    }

    @Test
    void concurrentReceiptRetriesAddStockOnlyOnce() throws Exception {
        long product = product();
        String receiptKey = UUID.randomUUID().toString();
        var request = Map.of("productId", product, "quantity", 7, "idempotencyKey", receiptKey);
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Callable<HttpResponse<String>>> tasks = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                tasks.add(() -> send(gateway, "POST", "/api/inventory/stocks", request, token(900001, "ADMIN")));
            }
            for (var result : executor.invokeAll(tasks)) {
                var response = result.get();
                assertEquals(200, response.statusCode());
                assertEquals(7, json.readTree(response.body()).path("data").path("available").asInt());
            }
        }
        assertStock(product, 7, 0);
        assertEquals(409, send(gateway, "POST", "/api/inventory/stocks",
                Map.of("productId", product, "quantity", 8, "idempotencyKey", receiptKey), token(900001, "ADMIN")).statusCode());
        assertStock(product, 7, 0);
    }

    @Test
    void stockReceiptLeavesRoomForReservedStockToBeReleased() throws Exception {
        long product = product();
        receive(product, 1);
        try (var connection = database(); var statement = connection.prepareStatement(
                "UPDATE inventory SET available=2147483646, reserved=1 WHERE product_id=?")) {
            statement.setLong(1, product);
            assertEquals(1, statement.executeUpdate());
        }
        var rejected = send(gateway, "POST", "/api/inventory/stocks",
                Map.of("productId", product, "quantity", 1, "idempotencyKey", UUID.randomUUID().toString()), token(900001, "ADMIN"));
        assertEquals(409, rejected.statusCode(), rejected.body());
        assertStock(product, 2147483646, 1);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ORDER_OUTBOX_FAILURE_TEST", matches = "true")
    void brokerOutageKeepsOrderAndOutboxThenRecovers() throws Exception {
        long product = product();
        receive(product, 1);
        Long orderId = null;
        try {
            docker("stop", "rabbitmq");
            var response = create(gateway, product, "outbox-" + UUID.randomUUID(), "WEB");
            assertEquals(201, response.statusCode());
            orderId = json.readTree(response.body()).path("data").path("id").asLong();
            assertEquals(1, eventCount(orderId, false));
            assertStock(product, 0, 1);
        } finally {
            docker("up", "-d", "--no-deps", "--wait", "--wait-timeout", "120", "rabbitmq");
        }
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline) && eventCount(orderId, true) != 1) {
            Thread.sleep(500);
        }
        assertEquals(1, eventCount(orderId, true), "Pending outbox must publish after broker recovery");
        assertEquals(200, send(gateway, "GET", "/api/orders/" + orderId, null, token(900001, "USER")).statusCode());
    }

    private int eventCount(
        long orderId,
        boolean published
    ) throws Exception {
        try (var connection = database(); var statement = connection.prepareStatement(
                "SELECT count(*) FROM outbox_events WHERE payload LIKE ? AND published_at IS "
                        + (published ? "NOT NULL" : "NULL"))) {
            statement.setString(1, "%\"orderId\":" + orderId + ",%");
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private void docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "-f", "docker-compose.yml", "-f", "docker-compose.local.yml"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).directory(Path.of(
                System.getenv().getOrDefault("ORDER_SMOKE_PROJECT_DIR", "..")).toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(150, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Docker operation timed out");
        }
        assertEquals(0, process.exitValue(), "Docker infrastructure operation failed");
    }

    private void receive(
        long product,
        int quantity
    ) throws Exception {
        assertEquals(403, send(gateway, "POST", "/api/inventory/stocks",
                Map.of("productId", product, "quantity", quantity, "idempotencyKey", UUID.randomUUID().toString()), token(900001, "USER")).statusCode());
        assertEquals(200, send(gateway, "POST", "/api/inventory/stocks",
                Map.of("productId", product, "quantity", quantity, "idempotencyKey", UUID.randomUUID().toString()), token(900001, "ADMIN")).statusCode());
    }

    private HttpResponse<String> create(
        String base,
        long product,
        String requestKey,
        String channel
    ) throws Exception {
        return send(base, "POST", "/api/orders", Map.of("channel", channel, "idempotencyKey", requestKey,
                "items", List.of(Map.of("productId", product, "quantity", 1))), token(900001, "USER"));
    }

    private void assertStock(
        long product,
        int available,
        int reserved
    ) throws Exception {
        var response = send(gateway, "GET", "/api/inventory/" + product, null, token(900001, "USER"));
        assertEquals(200, response.statusCode());
        JsonNode data = json.readTree(response.body()).path("data");
        assertEquals(available, data.path("available").asInt());
        assertEquals(reserved, data.path("reserved").asInt());
    }

    private java.sql.Connection database() throws Exception {
        return DriverManager.getConnection(System.getenv().getOrDefault("ORDER_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/order_db"), "order_user", System.getenv("ORDER_LIVE_DB_PASSWORD"));
    }

    private String token(
        long userId,
        String role
    ) {
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(System.getenv("JWT_SECRET")));
        return Jwts.builder().subject(Long.toString(userId)).issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }

    private HttpResponse<String> send(
        String base,
        String method,
        String path,
        Object body,
        String token
    ) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        request.header("Content-Type", "application/json").method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
