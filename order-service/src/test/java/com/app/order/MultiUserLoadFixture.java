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
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit operator tool. Each real Auth account owns a separate product/stock fixture. */
public final class MultiUserLoadFixture {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base = required("LOAD_BASE_URL");
    private final Path path = Path.of(required("LOAD_FIXTURE_FILE")).toAbsolutePath();

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Choose prepare, finish or verify");
        }
        var fixture = new MultiUserLoadFixture();
        switch (arguments[0]) {
            case "prepare" -> fixture.prepare();
            case "finish" -> fixture.inspect(true);
            case "verify" -> fixture.inspect(false);
            default -> throw new IllegalArgumentException("Unknown fixture operation");
        }
    }

    private void prepare() throws Exception {
        int accounts = Integer.parseInt(System.getenv().getOrDefault("LOAD_ACCOUNTS", "10"));
        int stock = Integer.parseInt(System.getenv().getOrDefault("LOAD_STOCK", "100"));
        if (accounts < 2 || accounts > 50 || stock < 1 || stock > 10000) {
            throw new IllegalArgumentException("Accounts must be 2..50 and stock 1..10000");
        }
        Files.createDirectories(path.getParent());
        // Reserve the name and restrict access before writing any credential.
        Files.createFile(path);
        restrict(path);
        String run = UUID.randomUUID().toString();
        String admin = Jwts.builder().subject("900006").issuer("omnichannel-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .claim("roles", List.of("ADMIN"))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(required("JWT_SECRET"))), Jwts.SIG.HS256)
                .compact();
        var users = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < accounts; index++) {
            String email = "multi-load-" + UUID.randomUUID() + "@example.com";
            String password = UUID.randomUUID().toString();
            expect(send("POST", "/api/auth/register", Map.of("fullName", "Load Fixture",
                    "email", email, "password", password), null), 201);
            var login = send("POST", "/api/auth/login", Map.of("email", email, "password", password), null);
            expect(login, 200);
            String token = json.readTree(login.body()).path("data").path("accessToken").asText();
            var me = send("GET", "/api/auth/me", null, token);
            expect(me, 200);
            long userId = json.readTree(me.body()).path("data").path("id").asLong();
            var product = send("POST", "/api/products", Map.of("sku", "MULTI-" + UUID.randomUUID(),
                    "name", "Multi-account load fixture", "unitPrice", 12.50), admin);
            expect(product, 201);
            long productId = json.readTree(product.body()).path("data").path("id").asLong();
            expect(send("POST", "/api/inventory/stocks", Map.of("productId", productId,
                    "quantity", stock, "idempotencyKey", UUID.randomUUID().toString()), admin), 200);
            if (token.isBlank() || userId <= 0 || productId <= 0) {
                throw new IllegalStateException("Invalid fixture API response");
            }
            users.add(Map.of("userId", userId, "userToken", token, "productId", productId, "initialStock", stock));
            // Save partial progress privately so a failed preparation retains its own data references.
            Files.writeString(path, json.writeValueAsString(Map.of("runId", run, "createdAt", Instant.now().toString(),
                    "adminToken", admin, "users", users)), StandardOpenOption.TRUNCATE_EXISTING);
        }
        System.out.println("PASS: " + accounts + " Auth accounts with isolated products; private fixture saved");
    }

    private void inspect(boolean finish) throws Exception {
        JsonNode fixture = json.readTree(Files.readString(path));
        String admin = fixture.path("adminToken").asText();
        var results = new ArrayList<Map<String, Object>>();
        try (var database = DriverManager.getConnection(required("ORDER_LIVE_DB_URL"), "order_user",
                required("ORDER_LIVE_DB_PASSWORD"))) {
            for (JsonNode user : fixture.path("users")) {
                long product = user.path("productId").asLong();
                String token = user.path("userToken").asText();
                var orders = new ArrayList<Long>();
                try (var statement = database.prepareStatement("SELECT DISTINCT o.id,o.user_id FROM orders o "
                        + "JOIN order_items i ON i.order_id=o.id WHERE i.product_id=? ORDER BY o.id")) {
                    statement.setLong(1, product);
                    try (var rows = statement.executeQuery()) {
                        while (rows.next()) {
                            if (rows.getLong(2) != user.path("userId").asLong()) {
                                throw new AssertionError("Fixture order belongs to another account");
                            }
                            orders.add(rows.getLong(1));
                        }
                    }
                }
                for (long order : orders) {
                    var payment = awaitPayment(order, token);
                    if (finish && payment.path("status").asText().equals("PENDING")) {
                        expect(send("POST", "/api/payments/" + payment.path("id").asLong() + "/simulate",
                                Map.of("status", "SUCCESS"), admin), 200);
                    }
                    Instant deadline = Instant.now().plusSeconds(45);
                    boolean confirmed = false;
                    while (Instant.now().isBefore(deadline)) {
                        var response = send("GET", "/api/orders/" + order, null, token);
                        expect(response, 200);
                        if (json.readTree(response.body()).path("data").path("status").asText().equals("CONFIRMED")) {
                            confirmed = true;
                            break;
                        }
                        if (!finish) {
                            throw new AssertionError("Load left an unconfirmed order");
                        }
                        Thread.sleep(500);
                    }
                    if (!confirmed || !awaitPayment(order, token).path("status").asText().equals("SUCCESS")) {
                        throw new AssertionError("Fixture payment/Saga did not finish");
                    }
                }
                var response = send("GET", "/api/inventory/" + product, null, token);
                expect(response, 200);
                JsonNode stock = json.readTree(response.body()).path("data");
                if (stock.path("reserved").asInt() != 0 || stock.path("available").asInt()
                        != user.path("initialStock").asInt() - orders.size()) {
                    throw new AssertionError("Fixture stock does not match confirmed PostgreSQL orders");
                }
                results.add(Map.of("userId", user.path("userId").asLong(), "productId", product,
                        "ordersConfirmed", orders.size(), "available", stock.path("available").asInt(), "reserved", 0));
            }
        }
        Files.writeString(Path.of(required("LOAD_RESULT_FILE")), json.writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("timeUtc", Instant.now().toString(), "runId", fixture.path("runId").asText(),
                        "accounts", results, "note", "API/SQL assertions passed; credentials excluded; data retained")));
        System.out.println("PASS: PostgreSQL ownership and API Payment/Order/stock reconciled for all accounts");
    }

    private JsonNode awaitPayment(long order, String token) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/payments/by-order/" + order, null, token);
            if (response.statusCode() == 200) {
                return json.readTree(response.body()).path("data");
            }
            expect(response, 404);
            Thread.sleep(500);
        }
        throw new AssertionError("Payment did not consume fixture order");
    }

    private HttpResponse<String> send(String method, String route, Object body, String token) throws Exception {
        HttpResponse<String> response = null;
        for (int retry = 0; retry < 12; retry++) {
            var request = HttpRequest.newBuilder(URI.create(base + route)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            String traceParent = System.getenv("TRACEPARENT");
            if (traceParent != null) {
                if (!traceParent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01")) {
                    throw new IllegalArgumentException("Invalid sampled fixture traceparent");
                }
                request.header("traceparent", traceParent);
            }
            response = client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429) {
                return response;
            }
            Thread.sleep(1000);
        }
        return response;
    }

    private static void restrict(Path file) throws Exception {
        var acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(file))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        } else {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private static void expect(HttpResponse<String> response, int status) {
        if (response.statusCode() != status) {
            throw new AssertionError("Unexpected HTTP status " + response.statusCode() + ", expected " + status);
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
