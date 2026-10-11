package com.app.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit load-tool fixture; never invoked by unit/liveTest or application startup. */
public class OrderLoadFixture {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base = required("LOAD_BASE_URL");
    private final Path fixturePath = Path.of(required("LOAD_FIXTURE_FILE")).toAbsolutePath();

    public static void main(String[] arguments) throws Exception {
        var fixture = new OrderLoadFixture();
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Choose prepare, verify-race or finish");
        }
        switch (arguments[0]) {
            case "prepare" -> fixture.prepare();
            case "verify-race" -> fixture.verifyRace();
            case "finish" -> fixture.finish();
            default -> throw new IllegalArgumentException("Unknown fixture operation");
        }
    }

    private void prepare() throws Exception {
        if (Files.exists(fixturePath)) {
            throw new IllegalStateException("Refusing to overwrite an existing private fixture");
        }
        String email = "day3-load-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString();
        expect(send("POST", "/api/auth/register", Map.of("fullName", "Day Three Load",
                "email", email, "password", password), null), 201);
        var login = send("POST", "/api/auth/login", Map.of("email", email, "password", password), null);
        for (int retry = 0; login.statusCode() == 429 && retry < 10; retry++) {
            Thread.sleep(1000);
            login = send("POST", "/api/auth/login", Map.of("email", email, "password", password), null);
        }
        expect(login, 200);
        String userToken = json.readTree(login.body()).path("data").path("accessToken").asText();
        if (userToken.isBlank()) {
            throw new IllegalStateException("Auth did not issue a token");
        }
        String admin = Jwts.builder().subject("900003").issuer("omnichannel-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .claim("roles", List.of("ADMIN"))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(required("JWT_SECRET"))), Jwts.SIG.HS256)
                .compact();
        long load = product("Day3 load fixture", 1000, admin);
        long race = product("Day3 race fixture", 1, admin);
        var fixture = new LinkedHashMap<String, Object>();
        fixture.put("runId", UUID.randomUUID().toString());
        fixture.put("createdAt", Instant.now().toString());
        fixture.put("userToken", userToken);
        fixture.put("adminToken", admin);
        fixture.put("loadProductId", load);
        fixture.put("raceProductId", race);
        Files.createDirectories(fixturePath.getParent());
        Files.writeString(fixturePath, json.writeValueAsString(fixture));
        System.out.println("PASS: isolated load/race products and private short-lived Auth fixture prepared");
    }

    private long product(String name, int quantity, String admin) throws Exception {
        var response = send("POST", "/api/products", Map.of("sku", "DAY3-" + UUID.randomUUID(),
                "name", name, "unitPrice", 12.50), admin);
        expect(response, 201);
        long id = json.readTree(response.body()).path("data").path("id").asLong();
        expect(send("POST", "/api/inventory/stocks", Map.of("productId", id, "quantity", quantity), admin), 200);
        return id;
    }

    private void verifyRace() throws Exception {
        JsonNode fixture = json.readTree(Files.readString(fixturePath));
        long race = fixture.path("raceProductId").asLong();
        try (var connection = database(); var statement = connection.prepareStatement(
                "SELECT available,reserved,(SELECT count(*) FROM order_items WHERE product_id=?) FROM inventory WHERE product_id=?")) {
            statement.setLong(1, race);
            statement.setLong(2, race);
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 0 || result.getInt(2) != 1 || result.getInt(3) != 1) {
                    throw new AssertionError("Race must leave available=0, reserved=1 and exactly one order in PostgreSQL");
                }
            }
        }
        System.out.println("PASS: PostgreSQL race available=0 reserved=1 orders=1");
    }

    private void finish() throws Exception {
        JsonNode fixture = json.readTree(Files.readString(fixturePath));
        List<Long> orders = new ArrayList<>();
        try (var connection = database(); var statement = connection.prepareStatement(
                "SELECT DISTINCT order_id FROM order_items WHERE product_id IN (?, ?) ORDER BY order_id")) {
            statement.setLong(1, fixture.path("loadProductId").asLong());
            statement.setLong(2, fixture.path("raceProductId").asLong());
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    orders.add(result.getLong(1));
                }
            }
        }
        String user = fixture.path("userToken").asText();
        String admin = fixture.path("adminToken").asText();
        for (long order : orders) {
            JsonNode payment = awaitPayment(order, user);
            expect(send("POST", "/api/payments/" + payment.path("id").asLong() + "/simulate",
                    Map.of("status", "SUCCESS"), admin), 200);
            Instant deadline = Instant.now().plusSeconds(45);
            boolean confirmed = false;
            while (Instant.now().isBefore(deadline)) {
                var response = send("GET", "/api/orders/" + order, null, user);
                expect(response, 200);
                if (json.readTree(response.body()).path("data").path("status").asText().equals("CONFIRMED")) {
                    confirmed = true;
                    break;
                }
                Thread.sleep(500);
            }
            if (!confirmed) {
                throw new AssertionError("Fixture Saga did not confirm order " + order);
            }
            Thread.sleep(100); // Respect Gateway order quota during fixture finalization.
        }
        var stocks = new ArrayList<JsonNode>();
        for (String field : List.of("loadProductId", "raceProductId")) {
            var stock = send("GET", "/api/inventory/" + fixture.path(field).asLong(), null, user);
            expect(stock, 200);
            JsonNode data = json.readTree(stock.body()).path("data");
            if (data.path("reserved").asInt() != 0 || data.path("available").asInt() < 0) {
                throw new AssertionError("Fixture stock still reserved or negative");
            }
            stocks.add(data);
        }
        Path report = Path.of(required("LOAD_RESULT_FILE"));
        Files.writeString(report, json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                "timeUtc", Instant.now().toString(), "ordersConfirmed", orders.size(),
                "orderIds", orders, "inventory", stocks,
                "note", "Real Gateway API and PostgreSQL checks; fixture tokens excluded; data retained")));
        System.out.println("PASS: " + orders.size() + " fixture orders confirmed through Saga; reserved=0; sanitized evidence saved");
    }

    private JsonNode awaitPayment(long order, String user) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/payments/by-order/" + order, null, user);
            if (response.statusCode() == 200) {
                return json.readTree(response.body()).path("data");
            }
            expect(response, 404);
            Thread.sleep(500);
        }
        throw new AssertionError("Payment did not consume fixture order");
    }

    private java.sql.Connection database() throws Exception {
        return DriverManager.getConnection(required("ORDER_LIVE_DB_URL"), "order_user", required("ORDER_LIVE_DB_PASSWORD"));
    }

    private HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void expect(HttpResponse<String> response, int expected) {
        if (response.statusCode() != expected) {
            // Do not include bodies/headers: Auth responses may contain credentials.
            throw new AssertionError("Unexpected HTTP status " + response.statusCode() + ", expected " + expected);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable " + name);
        }
        return value;
    }
}
