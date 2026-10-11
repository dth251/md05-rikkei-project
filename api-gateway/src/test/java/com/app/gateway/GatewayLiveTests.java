package com.app.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
class GatewayLiveTests {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String base = System.getenv().getOrDefault("GATEWAY_LIVE_URL", "http://127.0.0.1:8080");

    @Test
    @EnabledIfEnvironmentVariable(named = "GATEWAY_FAILURE_TEST", matches = "true")
    void productOutageReturnsControlled503WhileAuthRemainsAvailable() throws Exception {
        requireLocalTarget();
        String token = registerAndLogin();
        try {
            docker("stop", "product-service");
            assertEquals(503, send("GET", "/api/products", null, null).statusCode());
            assertEquals(200, send("GET", "/api/auth/me", null, token).statusCode());
            var unavailableOrder = send("POST", "/api/orders", Map.of(
                    "channel", "WEB", "idempotencyKey", UUID.randomUUID().toString(),
                    "items", java.util.List.of(Map.of("productId", 1, "quantity", 1))
            ), token);
            // A disconnected upstream is 503; the Gateway's response deadline is 504.
            assertTrue(unavailableOrder.statusCode() == 503 || unavailableOrder.statusCode() == 504);
            assertEquals(false, json.readTree(unavailableOrder.body()).path("success").asBoolean());
            assertEquals("api-gateway", unavailableOrder.headers().firstValue("X-Gateway").orElseThrow());
            assertEquals(200, send("GET", "/api/auth/me", null, token).statusCode());
        } finally {
            docker("up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "product-service");
        }
        assertEquals(200, send("GET", "/api/products", null, null).statusCode());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GATEWAY_FAILURE_TEST", matches = "true")
    void redisOutageFailsClosedForLoginAndOrderThenRecovers() throws Exception {
        requireLocalTarget();
        String token = registerAndLogin();
        try {
            docker("stop", "redis");
            assertEquals(503, send("POST", "/api/auth/login", Map.of(
                    "email", "unavailable@example.com", "password", "IncorrectPassword"
            ), null).statusCode());
            assertEquals(503, send("POST", "/api/orders", Map.of(
                    "channel", "WEB", "idempotencyKey", UUID.randomUUID().toString(),
                    "items", java.util.List.of(Map.of("productId", 1, "quantity", 1))
            ), token).statusCode());
            assertEquals(200, send("GET", "/api/products", null, null).statusCode());
            assertEquals(200, send("GET", "/api/auth/me", null, token).statusCode());
        } finally {
            docker("up", "-d", "--no-deps", "--wait", "--wait-timeout", "120", "redis");
        }
        Thread.sleep(2500);
        assertEquals(200, send("GET", "/api/auth/me", null, token).statusCode());
        assertEquals(401, send("POST", "/api/auth/login", Map.of(
                "email", "recovered@example.com", "password", "IncorrectPassword"
        ), null).statusCode());
    }

    private String registerAndLogin() throws Exception {
        Thread.sleep(3000);
        String email = "failure-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString();
        assertEquals(201, send("POST", "/api/auth/register", Map.of(
                "fullName", "Gateway recovery fixture", "email", email, "password", password
        ), null).statusCode());
        var login = send("POST", "/api/auth/login", Map.of("email", email, "password", password), null);
        assertEquals(200, login.statusCode());
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    private void requireLocalTarget() {
        assertEquals("127.0.0.1", URI.create(base).getHost(),
                "Container failure tests are restricted to the local test stack");
    }

    private void docker(String... arguments) throws Exception {
        var command = new java.util.ArrayList<>(java.util.List.of("docker", "compose",
                "-f", "docker-compose.yml", "-f", "docker-compose.local.yml"));
        command.addAll(java.util.List.of(arguments));
        var process = new ProcessBuilder(command).directory(Path.of("..").toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(210, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Project container operation timed out");
        }
        assertEquals(0, process.exitValue(), "Project container operation failed");
    }

    @Test
    void realAuthJwtAndRedisQuotaThroughGateway() throws Exception {
        String email = "gateway-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString();
        assertEquals(201, send("POST", "/api/auth/register", Map.of(
                "fullName", "Gateway Integration", "email", email, "password", password
        ), null).statusCode());
        var login = send("POST", "/api/auth/login", Map.of("email", email, "password", password), null);
        assertEquals(200, login.statusCode());
        String token = json.readTree(login.body()).path("data").path("accessToken").asText();
        assertTrue(!token.isBlank());
        var me = send("GET", "/api/auth/me", null, token);
        assertEquals(200, me.statusCode());
        assertEquals("api-gateway", me.headers().firstValue("X-Gateway").orElseThrow());
        assertTrue(me.headers().firstValue("X-Request-Id").isPresent());
        assertEquals(email, json.readTree(me.body()).path("data").path("email").asText());
        assertEquals(401, send("GET", "/api/auth/me", null, null).statusCode());
        assertEquals(401, send("GET", "/api/auth/me", null, "invalid").statusCode());
        long id = json.readTree(me.body()).path("data").path("id").asLong();
        assertEquals(403, send("GET", "/api/auth/admin/users/" + id, null, token).statusCode());
        int throttled = 0;
        var burst = new java.util.ArrayList<java.util.concurrent.Callable<HttpResponse<String>>>();
        for (int index = 0; index < 20; index++) {
            burst.add(() -> send("POST", "/api/auth/login", Map.of(
                    "email", email, "password", "IncorrectPassword"
            ), null));
        }
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(20)) {
            for (var result : executor.invokeAll(burst)) {
                var response = result.get();
                assertTrue(response.statusCode() == 401 || response.statusCode() == 429);
                if (response.statusCode() == 429) {
                    throttled++;
                }
            }
        }
        assertTrue(throttled > 0, "Redis login limit must produce 429");
        assertEquals(200, send("GET", "/api/auth/me", null, token).statusCode());
    }

    private HttpResponse<String> send(
            String method,
            String path,
            Object body,
            String token
    ) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .header("X-Real-IP", "203.0.113." + (Math.abs(UUID.randomUUID().hashCode() % 200) + 1));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
