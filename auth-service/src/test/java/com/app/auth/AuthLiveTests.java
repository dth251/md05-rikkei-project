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
        assertEquals(900, login.path("expiresIn").asLong());
        verifyRefreshAndAddresses(id, token, login.path("refreshToken").asText(), password);
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
                    "docker", "compose", "--project-directory",
                    System.getenv().getOrDefault("AUTH_SMOKE_PROJECT_DIR", ".."), "restart", "auth-service"
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
            Map<String, ?> body,
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
            Map<String, ?> body,
            String token
    ) throws Exception {
        var response = request(method, path, body, token);
        assertEquals(status, response.statusCode(), method + " " + path);
        if (status == 204) {
            return json.nullNode();
        }
        JsonNode responseBody = json.readTree(response.body());
        return path.equals("/actuator/health") ? responseBody : responseBody.path("data");
    }

    private void verifyRefreshAndAddresses(
            long userId,
            String accessToken,
            String refreshToken,
            String password
    ) throws Exception {
        assertEquals(43, refreshToken.length());
        List<Future<HttpResponse<String>>> results = new ArrayList<>();
        String successor = null;
        int winners = 0;
        try (var executor = Executors.newFixedThreadPool(6)) {
            for (int index = 0; index < 12; index++) {
                results.add(executor.submit(() -> request("POST", "/api/auth/refresh",
                        Map.of("refreshToken", refreshToken), null)));
            }
            for (var result : results) {
                var response = result.get(30, TimeUnit.SECONDS);
                if (response.statusCode() == 200) {
                    winners++;
                    successor = json.readTree(response.body()).path("data").path("refreshToken").asText();
                } else {
                    assertEquals(401, response.statusCode());
                }
            }
        }
        assertEquals(1, winners);
        expect("POST", "/api/auth/refresh", 401, Map.of("refreshToken", successor), null);
        expect("POST", "/api/auth/logout", 204, Map.of("refreshToken", refreshToken), null);

        Map<String, Object> address = Map.of("recipientName", "Live fixture", "phone", "0900000000",
                "street", "Fixture street", "ward", "Fixture ward", "district", "Fixture district",
                "province", "Fixture province", "defaultAddress", true);
        long first = expect("POST", "/api/auth/addresses", 201, address, accessToken).path("id").asLong();
        long second = expect("POST", "/api/auth/addresses", 201, address, accessToken).path("id").asLong();
        assertFalse(expect("GET", "/api/auth/addresses/" + first, 200, null, accessToken)
                .path("defaultAddress").asBoolean());
        assertTrue(expect("GET", "/api/auth/addresses/" + second, 200, null, accessToken)
                .path("defaultAddress").asBoolean());
        String otherEmail = UUID.randomUUID() + "@example.com";
        expect("POST", "/api/auth/register", 201,
                Map.of("fullName", "Other fixture", "email", otherEmail, "password", password), null);
        String other = expect("POST", "/api/auth/login", 200,
                Map.of("email", otherEmail, "password", password), null).path("accessToken").asText();
        expect("GET", "/api/auth/addresses/" + second, 404, null, other);
        expect("PUT", "/api/auth/addresses/" + second, 404, address, other);
        expect("DELETE", "/api/auth/addresses/" + second, 404, null, other);
        expect("DELETE", "/api/auth/addresses/" + second, 204, null, accessToken);
        assertTrue(expect("GET", "/api/auth/addresses/" + first, 200, null, accessToken)
                .path("defaultAddress").asBoolean());
        if (System.getenv("AUTH_SMOKE_DB_URL") != null) {
            try (var connection = DriverManager.getConnection(System.getenv("AUTH_SMOKE_DB_URL"),
                    "auth_user", System.getenv("AUTH_SMOKE_DB_PASSWORD"));
                 var statement = connection.prepareStatement("SELECT count(*) FROM refresh_tokens t "
                         + "JOIN refresh_sessions s ON s.id=t.session_id WHERE s.user_id=? AND length(t.token_hash)=64")) {
                statement.setLong(1, userId);
                try (var row = statement.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(2, row.getInt(1));
                }
            }
        }
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
