package com.app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live")
class AuthLiveTests {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper json = new ObjectMapper();

    private final String baseUrl = System.getenv().getOrDefault(
            "AUTH_SMOKE_URL",
            "http://127.0.0.1:8081"
    );

    @Test
    void realPostgresRegistrationSecurityConcurrencyAndRestart() throws Exception {
        waitForHealth();
        String email = "smoke-" + UUID.randomUUID() + "@example.com";
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Map<String, String> registration = Map.of(
                "fullName", "Day One Java Smoke",
                "email", email,
                "password", password
        );
        JsonNode user = expect("POST", "/api/auth/register", 201, registration, null);
        long id = user.path("id").asLong();
        assertEquals("USER", user.path("role").asText());
        assertFalse(user.has("passwordHash"));

        JsonNode login = expect("POST", "/api/auth/login", 200, Map.of(
                "email", email.toUpperCase(),
                "password", password
        ), null);
        String token = login.path("accessToken").asText();
        assertEquals("Bearer", login.path("tokenType").asText());
        assertEquals(id, expect("GET", "/api/auth/me", 200, null, token).path("id").asLong());
        expect("GET", "/api/auth/me", 401, null, null);
        expect("GET", "/api/auth/me", 401, null, "invalid");
        expect("GET", "/api/auth/admin/users/" + id, 403, null, token);
        expect("POST", "/api/auth/login", 401, Map.of(
                "email", email,
                "password", "WrongSecretForSmoke"
        ), null);
        expect("POST", "/api/auth/register", 409, registration, null);
        expect("POST", "/api/auth/register", 400, Map.of(
                "fullName", "Escalation User",
                "email", "other-" + email,
                "password", password,
                "role", "ADMIN"
        ), null);

        Map<String, String> concurrent = Map.of(
                "fullName", "Concurrent Java Smoke",
                "email", "race-" + UUID.randomUUID() + "@example.com",
                "password", password
        );
        List<Integer> statuses = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < 6; attempt++) {
                futures.add(executor.submit(
                        () -> request("POST", "/api/auth/register", concurrent, null).statusCode()
                ));
            }
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
        }
        assertEquals(1L, statuses.stream().filter(status -> status == 201).count());
        assertEquals(5L, statuses.stream().filter(status -> status == 409).count());

        if (System.getenv("AUTH_SMOKE_DB_URL") != null) {
            try (var connection = DriverManager.getConnection(
                    System.getenv("AUTH_SMOKE_DB_URL"),
                    "auth_user",
                    System.getenv("AUTH_SMOKE_DB_PASSWORD")
            )) {
                try (var query = connection.prepareStatement("SELECT password_hash FROM users WHERE id = ?")) {
                    query.setLong(1, id);
                    try (var result = query.executeQuery()) {
                        assertTrue(result.next());
                        String hash = result.getString(1);
                        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));
                        assertFalse(hash.equals(password));
                    }
                }
                try (var update = connection.prepareStatement("UPDATE users SET role = 'ADMIN' WHERE id = ?")) {
                    update.setLong(1, id);
                    assertEquals(1, update.executeUpdate());
                }
            }
            JsonNode admin = expect("POST", "/api/auth/login", 200, Map.of(
                    "email", email,
                    "password", password
            ), null);
            expect("GET", "/api/auth/admin/users/" + id, 200, null, admin.path("accessToken").asText());
        }

        if ("true".equals(System.getenv("AUTH_SMOKE_RESTART"))) {
            var process = new ProcessBuilder(
                    "docker", "compose", "--project-directory", "..", "restart", "auth-service"
            ).inheritIO().start();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            waitForHealth();
            JsonNode after = expect("POST", "/api/auth/login", 200, Map.of(
                    "email", email,
                    "password", password
            ), null);
            assertEquals(id, after.path("user").path("id").asLong());
        }
        System.out.println("PASS: Java live test; real PostgreSQL, API/RBAC, concurrent duplicate and persistence.");
    }

    private HttpResponse<String> request(
            String method,
            String path,
            Map<String, String> body,
            String token
    ) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
        );
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode expect(
            String method,
            String path,
            int status,
            Map<String, String> body,
            String token
    ) throws Exception {
        var response = request(method, path, body, token);
        assertEquals(status, response.statusCode(), method + " " + path);
        JsonNode responseBody = json.readTree(response.body());
        return path.equals("/actuator/health") ? responseBody : responseBody.path("data");
    }

    private void waitForHealth() throws Exception {
        for (int attempt = 0; attempt < 90; attempt++) {
            try {
                if ("UP".equals(expect("GET", "/actuator/health", 200, null, null).path("status").asText())) {
                    return;
                }
            } catch (Exception | AssertionError ignored) {
                Thread.sleep(1000);
            }
        }
        throw new IllegalStateException("Auth health did not become UP");
    }
}
