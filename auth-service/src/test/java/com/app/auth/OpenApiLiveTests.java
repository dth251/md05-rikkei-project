package com.app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAPI_LIVE_TEST", matches = "true")
class OpenApiLiveTests {

    @Test
    void deployedPrivateContractsRequireAdminAndMatchService() throws Exception {
        String secret = System.getenv("JWT_SECRET");
        assertTrue(secret != null && !secret.isBlank(), "Private JWT fixture configuration required");
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var json = new ObjectMapper();
        for (var service : Map.of(8081, "auth-service", 8082, "product-service",
                8083, "order-service", 8085, "payment-service").entrySet()) {
            URI uri = URI.create("http://127.0.0.1:" + service.getKey() + "/v3/api-docs");
            assertEquals(401, client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            for (String role : List.of("USER", "ADMIN")) {
                String token = Jwts.builder().issuer("omnichannel-auth").subject("1")
                        .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                        .expiration(Date.from(Instant.now().plusSeconds(120)))
                        .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
                var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + token).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(role.equals("ADMIN") ? 200 : 403, response.statusCode(), service.getValue());
                if (role.equals("ADMIN")) {
                    var contract = json.readTree(response.body());
                    assertEquals(service.getValue(), contract.path("info").path("title").asText());
                    assertFalse(contract.path("paths").has("/actuator/prometheus"));
                    assertTrue(contract.path("components").path("schemas").has("ApiError"));
                }
            }
        }
    }
}
