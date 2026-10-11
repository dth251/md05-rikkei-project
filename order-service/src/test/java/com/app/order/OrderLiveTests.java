package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live")
class OrderLiveTests {

    @Test
    void independentPostgresSchemaAndJwtProtectedSkeleton() throws Exception {
        String url = System.getenv().getOrDefault("ORDER_LIVE_DB_URL", "jdbc:postgresql://localhost:15432/order_db");
        String password = System.getenv("ORDER_LIVE_DB_PASSWORD");
        assertTrue(password != null && !password.isBlank(), "Order live DB password required");
        try (var connection = DriverManager.getConnection(url, "order_user", password)) {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT current_database(), current_user, "
                         + "(SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                         + "AND table_name IN ('orders', 'order_items', 'inventory'))")) {
                assertTrue(result.next());
                assertEquals("order_db", result.getString(1));
                assertEquals("order_user", result.getString(2));
                assertEquals(3, result.getInt(3));
            }
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                try {
                    statement.executeUpdate("INSERT INTO inventory (product_id, available, reserved, version) VALUES (-1, -1, 0, 0)");
                    throw new AssertionError("PostgreSQL must reject invalid inventory");
                } catch (SQLException exception) {
                    assertEquals("23514", exception.getSQLState());
                } finally {
                    connection.rollback();
                }
            }
        }
        String base = System.getenv().getOrDefault("ORDER_LIVE_URL", "http://127.0.0.1:8080");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var anonymous = client.send(HttpRequest.newBuilder(URI.create(base + "/api/orders"))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode());
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(System.getenv("JWT_SECRET")));
        String token = Jwts.builder().subject("1").issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("roles", List.of("USER")).signWith(key, Jwts.SIG.HS256).compact();
        var authenticated = client.send(HttpRequest.newBuilder(URI.create(base + "/api/orders"))
                .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, authenticated.statusCode(), "Order business API is intentionally a day 2 milestone");
        assertEquals("api-gateway", authenticated.headers().firstValue("X-Gateway").orElseThrow());
        assertEquals("Resource not found", new ObjectMapper().readTree(authenticated.body()).path("message").asText());
    }
}
