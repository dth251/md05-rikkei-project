package com.app.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Explicit operator fixture. It never runs as an application component or normal test. */
public final class ProductionDemoFixture {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base = required("DEMO_BASE_URL");
    private final Path directory = Path.of(required("DEMO_PRIVATE_DIR")).toAbsolutePath();
    private final Path fixtureFile = directory.resolve("fixture.private.json");
    private final Map<String, String> tokens = new LinkedHashMap<>();
    private ObjectNode fixture;

    public static void main(String[] args) throws Exception {
        var tool = new ProductionDemoFixture();
        if (!tool.base.equals("https://md05-rikkei-project.duckdns.org")
                && !tool.base.matches("http://127\\.0\\.0\\.1:[0-9]+")) {
            throw new IllegalArgumentException("Explicit project HTTPS or loopback target required");
        }
        switch (args.length == 0 ? "verify" : args[0]) {
            case "accounts" -> tool.accounts();
            case "seed" -> tool.seed();
            case "verify" -> tool.verify();
            case "capacity" -> tool.capacity();
            default -> throw new IllegalArgumentException("Expected accounts, seed, verify or capacity");
        }
    }

    private void accounts() throws Exception {
        Files.createDirectories(directory);
        restrict(directory);
        if (Files.exists(fixtureFile)) {
            load();
            if (!base.equals(fixture.path("baseUrl").asText())) {
                throw new IllegalArgumentException("Fixture belongs to another target");
            }
        } else {
            fixture = json.createObjectNode();
            fixture.put("runId", "demo-" + UUID.randomUUID());
            fixture.put("baseUrl", base);
            fixture.put("createdAt", Instant.now().toString());
            fixture.putObject("accounts");
            fixture.putObject("products");
            fixture.putObject("orders");
            fixture.putObject("addresses");
            for (String key : List.of("admin", "userA", "userB")) {
                var account = fixture.withObject("/accounts").putObject(key);
                account.put("email", fixture.path("runId").asText() + "-" + key.toLowerCase() + "@example.com");
                byte[] random = new byte[24];
                new SecureRandom().nextBytes(random);
                account.put("password", Base64.getUrlEncoder().withoutPadding().encodeToString(random));
                account.put("fullName", "MD05 Demo " + key);
            }
            save();
        }
        for (String key : List.of("admin", "userA", "userB")) {
            var account = (ObjectNode) fixture.path("accounts").path(key);
            if (!account.has("id")) {
                var registered = send("POST", "/api/auth/register", Map.of(
                        "fullName", account.path("fullName").asText(),
                        "email", account.path("email").asText(),
                        "password", account.path("password").asText()), null);
                expect(registered, 201, 409);
                var login = login(account);
                account.put("id", login.path("user").path("id").asLong());
                require(account.path("id").asLong() > 0, "Account id missing");
                save();
            }
        }
        System.out.println("PASS: three isolated accounts registered; operator must grant ADMIN to this fixture admin only");
    }

    private void seed() throws Exception {
        loadAndLogin();
        address("homeA", "userA", "123 Demo Street", true);
        address("workA", "userA", "456 Demo Office", false);
        address("homeB", "userB", "789 Demo Street", true);
        product("demoProduct", "Demo Shirt for interactive tests", "125000", "Clothing", 10);
        product("historyProduct", "Demo history fixture", "125000", "Clothing", 100);
        product("parentProduct", "Demo Shirt variants", "125000", "Clothing", 20);
        product("hoodieProduct", "Demo Hoodie", "350000", "Clothing", 50);
        product("mugProduct", "Demo Mug", "79000", "Home", 30);
        product("bagProduct", "Demo Backpack", "499000", "Accessories", 20);
        product("zeroStockProduct", "Demo Sold Out", "99000", "Home", 1);
        product("deletedProduct", "Demo inactive product", "59000", "Home", 5);
        variant("blueM", "Blue", "M", 20);
        variant("redL", "Red", "L", 15);
        variant("blackS", "Black", "S", 10);

        var zero = order("zeroStockConsumed", "ONLINE", "WEB", "userA", "zeroStockProduct");
        simulate(zero, "SUCCESS");
        awaitOrder(zero, "CONFIRMED");
        var inactive = send("GET", "/api/products/" + productId("deletedProduct"), null, null);
        expect(inactive, 200, 404);
        if (inactive.statusCode() == 200) {
            expect(send("DELETE", "/api/products/" + productId("deletedProduct"), null, admin()), 200);
        }

        online("onlineConfirmed", "WEB", "CONFIRMED");
        online("onlineShipping", "WEB", "SHIPPING");
        online("onlineCompleted", "WEB", "COMPLETED");
        online("posCompleted", "POS", "COMPLETED");
        var cancelled = order("onlineCancelled", "ONLINE", "WEB", "userA", "historyProduct");
        simulate(cancelled, "FAILED");
        awaitOrder(cancelled, "CANCELLED");

        cod("codConfirmed", "CONFIRMED", false);
        cod("codShipping", "SHIPPING", false);
        cod("codShippingPaid", "SHIPPING", true);
        cod("codCompleted", "COMPLETED", true);
        var codCancelled = order("codCancelled", "COD", "WEB", "userA", "historyProduct");
        expect(send("POST", orderRoute(codCancelled) + "/cod/cancel", null, admin()), 200);
        awaitOrder(codCancelled, "CANCELLED");
        awaitPayment(codCancelled, p -> p.path("status").asText().equals("FAILED"));

        returned("returnRequested", "REQUESTED");
        returned("returnApproved", "APPROVED");
        returned("returnRefundPending", "REFUND_PENDING");
        returned("returnRefunded", "REFUNDED");
        var foreign = order("foreignOwnedOrder", "ONLINE", "WEB", "userB", "historyProduct");
        simulate(foreign, "SUCCESS");
        awaitOrder(foreign, "CONFIRMED");

        var expired = order("onlineExpired", "ONLINE", "WEB", "userA", "historyProduct");
        System.out.println("Waiting for the real 150-second deadline; no timestamp or runtime TTL changes");
        poll(orderRoute(expired), owner(expired), o -> "EXPIRED".equals(o.path("status").asText()), 190);
        awaitPayment(expired, p -> p.path("reservationExpired").asBoolean());
        expect(send("POST", paymentRoute(expired) + "/simulate", Map.of("status", "SUCCESS"), admin()), 409);
        fixture.put("seededAt", Instant.now().toString());
        save();
        verify();
    }

    private void online(String key, String channel, String status) throws Exception {
        var order = order(key, "ONLINE", channel, "userA", "historyProduct");
        simulate(order, "SUCCESS");
        poll(orderRoute(order), owner(order), o -> List.of("CONFIRMED", "SHIPPING", "COMPLETED")
                .contains(o.path("status").asText()), 45);
        if (!status.equals("CONFIRMED")) {
            ship(order);
        }
        if (status.equals("COMPLETED")) {
            complete(order);
        }
    }

    private ObjectNode cod(String key, String status, boolean paid) throws Exception {
        var order = order(key, "COD", "WEB", "userA", "historyProduct");
        if (!status.equals("CONFIRMED")) {
            ship(order);
        }
        if (paid) {
            awaitPayment(order, p -> true);
            pollCollect(order);
            poll(orderRoute(order), owner(order), o -> o.hasNonNull("codPaidAt"), 45);
        }
        if (status.equals("COMPLETED")) {
            complete(order);
        }
        return order;
    }

    private void returned(String key, String status) throws Exception {
        var order = cod(key, "COMPLETED", true);
        String route = orderRoute(order) + "/return";
        expect(send("POST", route, Map.of("idempotencyKey", key(key, "return"),
                "reason", "Synthetic demo: whole-order return"), owner(order)), 200);
        if (!status.equals("REQUESTED")) {
            expect(send("POST", route + "/decision", Map.of("approved", true,
                    "note", "Synthetic demo return approved"), admin()), 200);
        }
        if (status.equals("REFUND_PENDING") || status.equals("REFUNDED")) {
            expect(send("POST", route + "/receive", Map.of("warehouseReceipt", key(key, "warehouse"),
                    "restock", true), admin()), 200);
            awaitPayment(order, p -> "REQUIRED".equals(p.path("refundStatus").asText())
                    || "REFUNDED".equals(p.path("refundStatus").asText()));
        }
        if (status.equals("REFUNDED")) {
            expect(send("POST", paymentRoute(order) + "/cod/refund", Map.of(
                    "amount", new BigDecimal("125000"), "idempotencyKey", key(key, "refund"),
                    "receiptNumber", key(key, "refund-receipt")), admin()), 200);
        }
        poll(route, owner(order), r -> status.equals(r.path("status").asText()), 45);
        order.put("expectedReturnStatus", status);
        save();
    }

    private void address(String key, String accountKey, String street, boolean primary) throws Exception {
        if (fixture.path("addresses").has(key)) {
            return;
        }
        var body = new LinkedHashMap<>(shippingAddress());
        body.put("street", street);
        body.put("defaultAddress", primary);
        var response = send("POST", "/api/auth/addresses", body, tokens.get(accountKey));
        expect(response, 201);
        fixture.withObject("/addresses").putObject(key).put("id", data(response).path("id").asLong())
                .put("owner", accountKey);
        save();
    }

    private void product(String key, String name, String price, String category, int stock) throws Exception {
        if (!fixture.path("products").has(key)) {
            var response = send("POST", "/api/products", productBody(key, name, price, category), admin());
            expect(response, 201);
            fixture.withObject("/products").putObject(key).put("id", data(response).path("id").asLong())
                    .put("name", name).put("price", price).put("initialStock", stock);
            save();
        }
        stock(key, stock);
    }

    private void variant(String key, String color, String size, int quantity) throws Exception {
        if (!fixture.path("products").has(key)) {
            var response = send("POST", "/api/products/" + productId("parentProduct") + "/variants", Map.of(
                    "product", productBody(key, "Demo Shirt " + color + " " + size, "130000", "Clothing"),
                    "attributes", Map.of("color", color, "size", size)), admin());
            expect(response, 201);
            fixture.withObject("/products").putObject(key).put("id", data(response).path("id").asLong())
                    .put("name", "Demo Shirt " + color + " " + size).put("price", "130000")
                    .put("parentId", productId("parentProduct")).put("initialStock", quantity);
            save();
        }
        stock(key, quantity);
    }

    private Map<String, Object> productBody(String key, String name, String price, String category) {
        return Map.of("sku", "DEMO-" + fixture.path("runId").asText().substring(5, 23) + "-" + key,
                "name", name, "description", "Synthetic MD05 demo fixture; " + fixture.path("runId").asText(),
                "unitPrice", new BigDecimal(price), "category", category, "brand", "MD05Demo");
    }

    private void stock(String key, int quantity) throws Exception {
        var product = (ObjectNode) fixture.path("products").path(key);
        if (product.path("stockProvisioned").asBoolean()) {
            return;
        }
        expect(send("POST", "/api/inventory/stocks", Map.of("productId", productId(key),
                "quantity", quantity, "idempotencyKey", key(key, "stock")), admin()), 200);
        product.put("stockProvisioned", true);
        save();
    }

    private ObjectNode order(String key, String method, String channel, String accountKey, String product) throws Exception {
        if (!fixture.path("orders").has(key)) {
            var response = send("POST", "/api/orders", Map.of("channel", channel, "paymentMethod", method,
                    "idempotencyKey", key(key, "order"), "items", List.of(Map.of("productId", productId(product),
                    "quantity", 1)), "shippingAddress", shippingAddress()), tokens.get(accountKey));
            expect(response, 201, 200);
            fixture.withObject("/orders").putObject(key).put("id", data(response).path("id").asLong())
                    .put("key", key).put("owner", accountKey).put("productKey", product)
                    .put("paymentMethod", method).put("channel", channel);
            save();
        }
        var order = (ObjectNode) fixture.path("orders").path(key);
        awaitPayment(order, p -> true);
        return order;
    }

    private Map<String, Object> shippingAddress() {
        return Map.of("recipientName", "MD05 Synthetic Demo", "phone", "0901234567",
                "street", "123 Demo Street", "ward", "Demo Ward", "district", "Demo District",
                "province", "Demo Province", "postalCode", "70000");
    }

    private void simulate(ObjectNode order, String status) throws Exception {
        expect(send("POST", paymentRoute(order) + "/simulate", Map.of("status", status), admin()), 200);
    }

    private void ship(ObjectNode order) throws Exception {
        expect(send("POST", orderRoute(order) + "/ship", Map.of("carrier", "MD05DemoCarrier",
                "trackingNumber", key(order.path("key").asText(), "tracking")), admin()), 200);
    }

    private void complete(ObjectNode order) throws Exception {
        expect(send("POST", orderRoute(order) + "/complete", null, admin()), 200);
    }

    private void pollCollect(ObjectNode order) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        do {
            var response = send("POST", paymentRoute(order) + "/cod/collect", Map.of("amount", new BigDecimal("125000"),
                    "idempotencyKey", key(order.path("key").asText(), "collect"),
                    "receiptNumber", key(order.path("key").asText(), "cash-receipt")), admin());
            if (response.statusCode() == 200) {
                return;
            }
            expect(response, 409);
            Thread.sleep(1000);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("COD shipment not acknowledged within limit");
    }

    private JsonNode awaitPayment(ObjectNode order, Predicate<JsonNode> ready) throws Exception {
        var payment = poll("/api/payments/by-order/" + order.path("id").asLong(), owner(order), ready, 45);
        order.put("paymentId", payment.path("id").asLong());
        save();
        return payment;
    }

    private void awaitOrder(ObjectNode order, String status) throws Exception {
        poll(orderRoute(order), owner(order), o -> status.equals(o.path("status").asText()), 45);
    }

    private JsonNode poll(String route, String token, Predicate<JsonNode> ready, int seconds) throws Exception {
        Instant deadline = Instant.now().plusSeconds(seconds);
        do {
            var response = send("GET", route, null, token);
            if (response.statusCode() == 200 && ready.test(data(response))) {
                return data(response);
            }
            expect(response, 200, 404);
            Thread.sleep(1000);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Polling timed out: " + route);
    }

    private void verify() throws Exception {
        loadAndLogin();
        require(fixture.path("accounts").size() == 3 && fixture.path("addresses").size() == 3
                && fixture.path("products").size() == 11 && fixture.path("orders").size() == 17,
                "Complete fixture requires 3 accounts, 3 addresses, 11 products and 17 orders");
        var report = json.createObjectNode();
        report.put("runId", fixture.path("runId").asText());
        report.put("baseUrl", base);
        report.put("verifiedAt", Instant.now().toString());
        report.set("accounts", json.createObjectNode());
        for (String key : List.of("admin", "userA", "userB")) {
            var account = fixture.path("accounts").path(key);
            report.withObject("/accounts").putObject(key).put("id", account.path("id").asLong())
                    .put("email", account.path("email").asText()).put("role", key.equals("admin") ? "ADMIN" : "USER");
        }
        var products = report.putObject("products");
        for (var iterator = fixture.path("products").properties().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            String key = entry.getKey();
            var productResponse = send("GET", "/api/products/" + productId(key), null, null);
            expect(productResponse, key.equals("deletedProduct") ? 404 : 200);
            var inventoryResponse = send("GET", "/api/inventory/" + productId(key), null, tokens.get("userA"));
            expect(inventoryResponse, 200);
            var inventory = data(inventoryResponse);
            int expectedAvailable = key.equals("historyProduct") ? 89 : key.equals("zeroStockProduct") ? 0
                    : entry.getValue().path("initialStock").asInt();
            int expectedReserved = key.equals("historyProduct") ? 1 : 0;
            require(inventory.path("available").asInt() == expectedAvailable
                    && inventory.path("reserved").asInt() == expectedReserved, "Unexpected inventory: " + key);
            var summary = products.putObject(key);
            if (productResponse.statusCode() == 200) {
                summary.setAll((ObjectNode) data(productResponse));
            } else {
                summary.put("sku", productBody(key, "Inactive", "59000", "Home").get("sku").toString()
                        .toUpperCase(java.util.Locale.ROOT));
            }
            summary.setAll((ObjectNode) entry.getValue());
            summary.put("available", expectedAvailable).put("reserved", expectedReserved)
                    .put("active", !key.equals("deletedProduct"));
        }
        var addresses = report.putObject("addresses");
        for (var iterator = fixture.path("addresses").properties().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var addressResponse = send("GET", "/api/auth/addresses/" + entry.getValue().path("id").asLong(), null,
                    tokens.get(entry.getValue().path("owner").asText()));
            expect(addressResponse, 200);
            var addressSummary = addresses.putObject(entry.getKey());
            addressSummary.setAll((ObjectNode) data(addressResponse));
            addressSummary.put("owner", entry.getValue().path("owner").asText());
        }
        var orders = report.putObject("orders");
        Map<String, String> statuses = Map.ofEntries(Map.entry("onlineConfirmed", "CONFIRMED"),
                Map.entry("onlineShipping", "SHIPPING"), Map.entry("onlineCompleted", "COMPLETED"),
                Map.entry("onlineCancelled", "CANCELLED"), Map.entry("onlineExpired", "EXPIRED"),
                Map.entry("posCompleted", "COMPLETED"), Map.entry("codConfirmed", "CONFIRMED"),
                Map.entry("codShipping", "SHIPPING"), Map.entry("codShippingPaid", "SHIPPING"),
                Map.entry("codCompleted", "COMPLETED"), Map.entry("codCancelled", "CANCELLED"),
                Map.entry("zeroStockConsumed", "CONFIRMED"), Map.entry("foreignOwnedOrder", "CONFIRMED"));
        for (var iterator = fixture.path("orders").properties().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var order = (ObjectNode) entry.getValue();
            var response = send("GET", orderRoute(order), null, owner(order));
            expect(response, 200);
            var current = data(response);
            String expected = statuses.getOrDefault(entry.getKey(), "COMPLETED");
            require(expected.equals(current.path("status").asText()), "Unexpected order status: " + entry.getKey());
            require(order.path("paymentMethod").asText().equals(current.path("paymentMethod").asText())
                    && order.path("channel").asText().equals(current.path("channel").asText()),
                    "Unexpected order method/channel: " + entry.getKey());
            var summary = orders.putObject(entry.getKey());
            summary.setAll(order);
            summary.put("status", expected).put("createdAt", current.path("createdAt").asText())
                    .put("totalAmount", current.path("totalAmount").decimalValue())
                    .put("codPaid", current.hasNonNull("codPaidAt"));
            var payment = awaitPayment(order, p -> true);
            String expectedPayment = List.of("onlineCancelled", "onlineExpired", "codCancelled").contains(entry.getKey())
                    ? "FAILED" : List.of("codConfirmed", "codShipping").contains(entry.getKey()) ? "PENDING" : "SUCCESS";
            String expectedRefund = entry.getKey().equals("returnRefundPending") ? "REQUIRED"
                    : entry.getKey().equals("returnRefunded") ? "REFUNDED" : "NONE";
            require(expectedPayment.equals(payment.path("status").asText())
                    && expectedRefund.equals(payment.path("refundStatus").asText())
                    && payment.path("amount").decimalValue().compareTo(current.path("totalAmount").decimalValue()) == 0,
                    "Unexpected payment amount/status/refund: " + entry.getKey());
            summary.put("paymentStatus", payment.path("status").asText())
                    .put("refundStatus", payment.path("refundStatus").asText())
                    .put("reservationExpired", payment.path("reservationExpired").asBoolean());
            if (order.has("expectedReturnStatus")) {
                var returned = send("GET", orderRoute(order) + "/return", null, owner(order));
                expect(returned, 200);
                require(order.path("expectedReturnStatus").asText().equals(data(returned).path("status").asText()),
                        "Unexpected return stage: " + entry.getKey());
                summary.put("returnStatus", data(returned).path("status").asText());
            }
            String other = order.path("owner").asText().equals("userA") ? tokens.get("userB") : tokens.get("userA");
            expect(send("GET", orderRoute(order), null, other), 404);
            expect(send("GET", paymentRoute(order), null, other), 404);
        }
        expect(send("GET", "/api/auth/me", null, null), 401);
        expect(send("POST", "/api/products", productBody("denied", "Denied", "10000", "Home"), tokens.get("userA")), 403);
        privateWrite(directory.resolve("manifest.json"), report);
        exportEnvironment();
        System.out.println("PASS: three accounts, three addresses, eleven products and seventeen orders verified via HTTPS; ownership 404 and role 403 passed");
    }

    private void capacity() throws Exception {
        loadAndLogin();
        var credentials = fixture.withArray("/capacityAccounts");
        var capacity = json.createObjectNode();
        capacity.put("runId", fixture.path("runId").asText());
        capacity.put("createdAt", Instant.now().toString());
        var users = capacity.putArray("users");
        var summary = json.createObjectNode();
        summary.put("runId", fixture.path("runId").asText());
        var accounts = summary.putArray("accounts");
        for (int index = 0; index < 50; index++) {
            ObjectNode account;
            if (index >= credentials.size()) {
                account = credentials.addObject();
                account.put("email", fixture.path("runId").asText() + "-load" + index + "@example.com");
                account.put("fullName", "MD05 Demo Load " + index);
                byte[] random = new byte[24];
                new SecureRandom().nextBytes(random);
                account.put("password", Base64.getUrlEncoder().withoutPadding().encodeToString(random));
                save();
            } else {
                account = (ObjectNode) credentials.get(index);
            }
            if (!account.has("id")) {
                expect(send("POST", "/api/auth/register", Map.of("email", account.path("email").asText(),
                        "fullName", account.path("fullName").asText(), "password", account.path("password").asText()), null), 201, 409);
            }
            var login = login(account);
            require("USER".equals(login.path("user").path("role").asText()), "Capacity account must be USER");
            account.put("id", login.path("user").path("id").asLong());
            save();
            users.addObject().put("userId", account.path("id").asLong()).put("userToken", login.path("accessToken").asText());
            accounts.addObject().put("id", account.path("id").asLong()).put("email", account.path("email").asText())
                    .put("role", "USER");
        }
        expect(send("GET", "/api/products?page=0&size=20", null, users.get(0).path("userToken").asText()), 200);
        summary.put("verifiedAt", Instant.now().toString()).put("accounts", 50);
        // Keep a separate array from the count so the manifest carries all account identities.
        summary.set("identities", accounts);
        privateWrite(directory.resolve("capacity-50.private.json"), capacity);
        privateWrite(directory.resolve("capacity-manifest.json"), summary);
        System.out.println("PASS: 50 real USER accounts with fresh private tokens; catalog HTTPS 200; no load generated");
    }

    private void exportEnvironment() throws Exception {
        var environment = json.createObjectNode();
        environment.put("id", UUID.randomUUID().toString());
        environment.put("name", "MD05 production demo " + fixture.path("runId").asText());
        environment.put("_postman_variable_scope", "environment");
        var values = environment.putArray("values");
        var variables = new LinkedHashMap<String, String>();
        variables.put("baseUrl", base);
        variables.put("runId", fixture.path("runId").asText());
        for (String accountKey : List.of("admin", "userA", "userB")) {
            var account = fixture.path("accounts").path(accountKey);
            String emailKey = accountKey.equals("admin") ? "adminEmail" : accountKey.equals("userA") ? "email" : "emailB";
            String passwordKey = accountKey.equals("admin") ? "adminPassword" : accountKey.equals("userA") ? "password" : "passwordB";
            String tokenKey = accountKey.equals("admin") ? "adminToken" : accountKey.equals("userA") ? "userToken" : "userBToken";
            variables.put(emailKey, account.path("email").asText());
            variables.put(passwordKey, account.path("password").asText());
            variables.put(tokenKey, tokens.get(accountKey));
            variables.put(accountKey + "Id", account.path("id").asText());
        }
        variables.put("productId", Long.toString(productId("demoProduct")));
        variables.put("userId", fixture.path("accounts").path("userA").path("id").asText());
        variables.put("addressId", fixture.path("addresses").path("homeA").path("id").asText());
        for (var iterator = fixture.path("products").properties().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            variables.put(entry.getKey() + "Id", entry.getValue().path("id").asText());
        }
        for (var iterator = fixture.path("orders").properties().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            variables.put(entry.getKey() + "OrderId", entry.getValue().path("id").asText());
            variables.put(entry.getKey() + "PaymentId", entry.getValue().path("paymentId").asText());
        }
        variables.put("receiptKey", key("interactive", "receipt"));
        variables.put("orderKey", key("interactive", "order"));
        variables.put("invalidToken", "invalid");
        variables.put("orderId", fixture.path("orders").path("onlineConfirmed").path("id").asText());
        variables.put("paymentId", fixture.path("orders").path("onlineConfirmed").path("paymentId").asText());
        for (var entry : variables.entrySet()) {
            values.addObject().put("key", entry.getKey()).put("value", entry.getValue()).put("enabled", true)
                    .put("type", entry.getKey().toLowerCase().matches(".*(password|token).*" ) ? "secret" : "default");
        }
        privateWrite(directory.resolve("production-demo.postman_environment.json"), environment);
    }

    private void loadAndLogin() throws Exception {
        load();
        require(base.equals(fixture.path("baseUrl").asText()), "Fixture belongs to another target");
        for (String key : List.of("admin", "userA", "userB")) {
            var login = login(fixture.path("accounts").path(key));
            require((key.equals("admin") ? "ADMIN" : "USER").equals(login.path("user").path("role").asText()),
                    "Unexpected account role: " + key);
            tokens.put(key, login.path("accessToken").asText());
        }
    }

    private JsonNode login(JsonNode account) throws Exception {
        var response = send("POST", "/api/auth/login", Map.of("email", account.path("email").asText(),
                "password", account.path("password").asText()), null);
        expect(response, 200);
        return data(response);
    }

    private HttpResponse<String> send(String method, String route, Object body, String token) throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            var request = HttpRequest.newBuilder(URI.create(base + route)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            var response = client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429) {
                Thread.sleep(120);
                return response;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Rate limit persists: " + method + " " + route);
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        var envelope = json.readTree(response.body());
        require(envelope.path("success").asBoolean() && envelope.hasNonNull("data"), "Successful API envelope required");
        return envelope.path("data");
    }

    private void load() throws Exception {
        fixture = (ObjectNode) json.readTree(Files.readString(fixtureFile));
    }

    private void save() throws Exception {
        privateWrite(fixtureFile, fixture);
    }

    private void privateWrite(Path file, JsonNode content) throws Exception {
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
        restrict(file);
        Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(content));
    }

    private static void restrict(Path file) throws Exception {
        var acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(file))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        } else {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(Files.isDirectory(file) ? "rwx------" : "rw-------"));
        }
    }

    private long productId(String key) {
        return fixture.path("products").path(key).path("id").asLong();
    }

    private String key(String name, String stage) {
        return fixture.path("runId").asText() + "-" + name + "-" + stage;
    }

    private String owner(ObjectNode order) {
        return tokens.get(order.path("owner").asText());
    }

    private String admin() {
        return tokens.get("admin");
    }

    private String orderRoute(ObjectNode order) {
        return "/api/orders/" + order.path("id").asLong();
    }

    private String paymentRoute(ObjectNode order) {
        return "/api/payments/" + order.path("paymentId").asLong();
    }

    private static void expect(HttpResponse<String> response, int... statuses) {
        for (int status : statuses) {
            if (response.statusCode() == status) {
                return;
            }
        }
        throw new AssertionError("Unexpected HTTP status: " + response.statusCode());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
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
