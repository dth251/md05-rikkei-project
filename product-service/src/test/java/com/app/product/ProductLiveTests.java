package com.app.product;

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

@Tag("live")
class ProductLiveTests {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String base = System.getenv().getOrDefault("PRODUCT_LIVE_URL", "http://127.0.0.1:8080");

    @Test
    void postgresVariantRaceLiteralSearchAndParentRetirement() throws Exception {
        String admin = token("ADMIN");
        String category = UUID.randomUUID().toString();
        var parentResponse = send("POST", "/api/products", Map.of(
                "sku", "PARENT-" + UUID.randomUUID(), "name", "Search % fixture", "category", category,
                "brand", "Fixture Brand", "unitPrice", "10.00"), admin);
        assertEquals(201, parentResponse.statusCode());
        long parent = json.readTree(parentResponse.body()).path("data").path("id").asLong();
        try (var executor = Executors.newFixedThreadPool(8)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                boolean upper = index % 2 == 0;
                results.add(executor.submit(() -> {
                    start.await();
                    return send("POST", "/api/products/" + parent + "/variants", Map.of(
                            "product", Map.of("sku", "CHILD-" + UUID.randomUUID(), "name", "Blue medium",
                                    "category", category, "brand", "Fixture Brand", "unitPrice", "12.50"),
                            "attributes", Map.of("color", upper ? "\u00A0BLUE\u00A0" : "blue", "size", upper ? "M" : "m")),
                            admin).statusCode();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) {
                statuses.add(result.get());
            }
            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(19, statuses.stream().filter(status -> status == 409).count());
        }
        JsonNode variants = json.readTree(send("GET", "/api/products/" + parent + "/variants", null, null)
                .body()).path("data");
        assertEquals(1, variants.size());
        assertEquals("blue", variants.get(0).path("attributes").path("color").asText());
        long child = variants.get(0).path("id").asLong();
        var filtered = send("GET", "/api/products?category=" + category + "&q=%25&sort=price-desc", null, null);
        assertEquals(200, filtered.statusCode());
        assertEquals(1, json.readTree(filtered.body()).path("data").path("totalElements").asInt());
        assertEquals(400, send("GET", "/api/products?minPrice=20&maxPrice=10", null, null).statusCode());
        assertEquals(200, send("DELETE", "/api/products/" + parent, null, admin).statusCode());
        assertEquals(404, send("GET", "/api/products/" + child, null, null).statusCode());
        assertEquals(0, json.readTree(send("GET", "/api/products?category=" + category, null, null)
                .body()).path("data").path("totalElements").asInt());
    }

    @Test
    void variantHasIndependentStockAndImmutableOrderSnapshot() throws Exception {
        String admin = token("ADMIN");
        String user = token("USER");
        var parentResponse = send("POST", "/api/products", Map.of("sku", "SELL-" + UUID.randomUUID(),
                "name", "Sellable parent", "unitPrice", "10.00"), admin);
        assertEquals(201, parentResponse.statusCode());
        long parent = json.readTree(parentResponse.body()).path("data").path("id").asLong();
        String sku = "SELL-VAR-" + UUID.randomUUID();
        var childResponse = send("POST", "/api/products/" + parent + "/variants", Map.of(
                "product", Map.of("sku", sku, "name", "Blue medium", "unitPrice", "12.50"),
                "attributes", Map.of("color", "blue", "size", "m")), admin);
        assertEquals(201, childResponse.statusCode());
        long child = json.readTree(childResponse.body()).path("data").path("id").asLong();
        assertEquals(200, send("POST", "/api/inventory/stocks", Map.of("productId", child, "quantity", 2,
                "idempotencyKey", UUID.randomUUID().toString()), admin).statusCode());
        var orderResponse = send("POST", "/api/orders", Map.of("channel", "POS",
                "idempotencyKey", UUID.randomUUID().toString(),
                "items", List.of(Map.of("productId", child, "quantity", 1))), user);
        assertEquals(201, orderResponse.statusCode());
        long order = json.readTree(orderResponse.body()).path("data").path("id").asLong();
        JsonNode inventory = json.readTree(send("GET", "/api/inventory/" + child, null, user).body()).path("data");
        assertEquals(1, inventory.path("available").asInt());
        assertEquals(1, inventory.path("reserved").asInt());
        JsonNode payment = null;
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/payments/by-order/" + order, null, user);
            if (response.statusCode() == 200) {
                payment = json.readTree(response.body()).path("data");
                break;
            }
            assertEquals(404, response.statusCode());
            Thread.sleep(300);
        }
        assertTrue(payment != null, "Variant order must reach Payment through Outbox");
        assertEquals(200, send("POST", "/api/payments/" + payment.path("id").asLong() + "/simulate",
                Map.of("status", "SUCCESS"), admin).statusCode());
        deadline = Instant.now().plusSeconds(45);
        JsonNode snapshot = null;
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/orders/" + order, null, user);
            assertEquals(200, response.statusCode());
            snapshot = json.readTree(response.body()).path("data");
            if ("CONFIRMED".equals(snapshot.path("status").asText())) {
                break;
            }
            Thread.sleep(300);
        }
        assertEquals("CONFIRMED", snapshot.path("status").asText());
        assertEquals(200, send("DELETE", "/api/products/" + parent, null, admin).statusCode());
        snapshot = json.readTree(send("GET", "/api/orders/" + order, null, user).body()).path("data");
        assertEquals(sku.toUpperCase(java.util.Locale.ROOT), snapshot.path("items").get(0).path("sku").asText());
        assertEquals(0, new java.math.BigDecimal("12.50")
                .compareTo(snapshot.path("items").get(0).path("unitPrice").decimalValue()));
        inventory = json.readTree(send("GET", "/api/inventory/" + child, null, user).body()).path("data");
        assertEquals(1, inventory.path("available").asInt());
        assertEquals(0, inventory.path("reserved").asInt());
    }

    @Test
    void realPostgresCrudRolesAndConcurrentUniqueSkuThroughGateway() throws Exception {
        String admin = token("ADMIN");
        String user = token("USER");
        Map<String, Object> product = Map.of(
                "sku", "LIVE-" + UUID.randomUUID(), "name", "Integration Product", "unitPrice", "12.50"
        );
        assertEquals(401, send("POST", "/api/products", product, null).statusCode());
        assertEquals(403, send("POST", "/api/products", product, user).statusCode());
        var created = send("POST", "/api/products", product, admin);
        assertEquals(201, created.statusCode());
        long id = json.readTree(created.body()).path("data").path("id").asLong();
        var read = send("GET", "/api/products/" + id, null, null);
        assertEquals(200, read.statusCode());
        assertEquals("api-gateway", read.headers().firstValue("X-Gateway").orElseThrow());
        assertEquals(200, send("PUT", "/api/products/" + id, Map.of(
                "sku", product.get("sku"), "name", "Updated Product", "unitPrice", "20.00"
        ), admin).statusCode());
        assertEquals(409, send("POST", "/api/products", product, admin).statusCode());
        assertEquals(400, send("POST", "/api/products", Map.of(
                "sku", "INVALID", "name", "Invalid", "unitPrice", "-1.00"
        ), admin).statusCode());
        Map<String, Object> raceProduct = Map.of(
                "sku", "RACE-" + UUID.randomUUID(), "name", "Concurrent Product", "unitPrice", "1.00"
        );
        try (var executor = Executors.newFixedThreadPool(6)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> requests = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                requests.add(executor.submit(() -> {
                    start.await();
                    return send("POST", "/api/products", raceProduct, admin).statusCode();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var request : requests) {
                statuses.add(request.get());
            }
            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(5, statuses.stream().filter(status -> status == 409).count());
        }
        assertEquals(200, send("DELETE", "/api/products/" + id, null, admin).statusCode());
        assertEquals(404, send("GET", "/api/products/" + id, null, null).statusCode());
        JsonNode page = json.readTree(send("GET", "/api/products?size=100", null, null).body()).path("data");
        for (var item : page.path("content")) {
            assertTrue(item.path("id").asLong() != id, "Soft-deleted product must be absent from public catalog");
        }
    }

    private String token(
            String role
    ) {
        String secret = System.getenv("JWT_SECRET");
        assertTrue(secret != null && !secret.isBlank(), "JWT_SECRET is required for live test fixtures");
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        return Jwts.builder().subject("1").issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }

    private HttpResponse<String> send(
            String method,
            String path,
            Object body,
            String token
    ) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
