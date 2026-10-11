package com.app.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live")
class GatewayLiveTests {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String base = System.getenv().getOrDefault("GATEWAY_LIVE_URL", "http://127.0.0.1:8080");

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
        for (int index = 0; index < 20; index++) {
            var response = send("POST", "/api/auth/login", Map.of(
                    "email", email, "password", "IncorrectPassword"
            ), null);
            assertTrue(response.statusCode() == 401 || response.statusCode() == 429);
            if (response.statusCode() == 429) {
                throttled++;
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
