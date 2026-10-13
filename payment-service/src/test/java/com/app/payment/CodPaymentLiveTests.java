package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

@Tag("live")
class CodPaymentLiveTests extends PaymentLiveFixture {

    @BeforeEach
    void gatewayReachesRecreatedServices() throws Exception {
        for (String path : List.of("/api/orders/-1", "/api/payments/-1")) {
            Instant deadline = Instant.now().plusSeconds(45);
            int code;
            do {
                code = send("GET", path, null, token(900101, "ADMIN")).statusCode();
                if (code == 404) {
                    break;
                }
                assertTrue(code == 503 || code == 504, "Unexpected readiness HTTP status: " + code);
                Thread.sleep(300);
            } while (Instant.now().isBefore(deadline));
            assertEquals(404, code, "Gateway must reach the service before business assertions");
        }
    }

    @Test
    void gatewayCodCollectsOnceAndCompletesOnlyAfterCashAcknowledgement() throws Exception {
        JsonNode order = createCod();
        long orderId = order.path("id").asLong();
        JsonNode payment = await("/api/payments/by-order/" + orderId, "PENDING");
        long paymentId = payment.path("id").asLong();
        assertEquals("COD", payment.path("paymentMethod").asText());
        var receipt = receipt(payment);
        String collection = "/api/payments/" + paymentId + "/cod/collect";
        assertEquals(403, send("POST", collection, receipt, token(900101, "USER")).statusCode());
        assertEquals(409, send("POST", collection, receipt, token(900101, "ADMIN")).statusCode());
        assertEquals(409, simulate(paymentId, "SUCCESS", "ADMIN").statusCode());
        assertEquals(409, send("POST", "/api/orders/" + orderId + "/complete", null, token(900101, "ADMIN")).statusCode());
        ship(orderId);
        awaitShipment(orderId);
        assertEquals(409, send("POST", "/api/orders/" + orderId + "/complete", null, token(900101, "ADMIN")).statusCode());
        stock(orderId, 1, 0);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            tasks.add(() -> send("POST", collection, receipt, token(900101, "ADMIN")).statusCode());
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(tasks)) {
                assertEquals(200, result.get());
            }
        }
        JsonNode captured = await("/api/payments/" + paymentId, "SUCCESS");
        assertEquals(900101, captured.path("codReceipt").path("collectedBy").asLong());
        assertEquals(receipt.get("receiptNumber"), captured.path("codReceipt").path("receiptNumber").asText());
        assertEquals(409, send("POST", collection, receipt(payment), token(900101, "ADMIN")).statusCode());
        awaitPaidOrder(orderId);
        assertEquals(200, send("POST", "/api/orders/" + orderId + "/complete", null, token(900101, "ADMIN")).statusCode());
        assertEquals(200, send("POST", "/api/orders/" + orderId + "/complete", null, token(900101, "ADMIN")).statusCode());
        await("/api/orders/" + orderId, "COMPLETED");
        assertOutcome(orderId, "payment.succeeded");
        awaitNotification(orderId, "order.confirmed");
        stock(orderId, 1, 0);
    }

    @Test
    void gatewayCancellationBeforeShipmentReleasesReservationAndFailsPayment() throws Exception {
        JsonNode order = createCod();
        long orderId = order.path("id").asLong();
        JsonNode payment = await("/api/payments/by-order/" + orderId, "PENDING");
        String cancellation = "/api/orders/" + orderId + "/cod/cancel";
        assertEquals(403, send("POST", cancellation, null, token(900101, "USER")).statusCode());
        assertEquals(200, send("POST", cancellation, null, token(900101, "ADMIN")).statusCode());
        assertEquals(200, send("POST", cancellation, null, token(900101, "ADMIN")).statusCode());
        await("/api/payments/" + payment.path("id").asLong(), "FAILED");
        assertEquals(409, send("POST", "/api/payments/" + payment.path("id").asLong() + "/cod/collect",
                receipt(payment), token(900101, "ADMIN")).statusCode());
        assertEquals(409, send("POST", "/api/orders/" + orderId + "/ship", shipment(orderId), token(900101, "ADMIN")).statusCode());
        stock(orderId, 2, 0);
        assertOutcome(orderId, "payment.failed");
        awaitNotification(orderId, "order.cancelled");
    }

    @Test
    void concurrentShipmentAndCancellationCommitOnlyOneStockTransition() throws Exception {
        JsonNode order = createCod();
        long orderId = order.path("id").asLong();
        await("/api/payments/by-order/" + orderId, "PENDING");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<Callable<Integer>>of(
                    () -> send("POST", "/api/orders/" + orderId + "/ship", shipment(orderId), token(900101, "ADMIN")).statusCode(),
                    () -> send("POST", "/api/orders/" + orderId + "/cod/cancel", null, token(900101, "ADMIN")).statusCode()));
            var codes = List.of(results.get(0).get(), results.get(1).get());
            assertEquals(1, codes.stream().filter(code -> code == 200).count());
            assertEquals(1, codes.stream().filter(code -> code == 409).count());
        }
        JsonNode finalOrder = json.readTree(send("GET", "/api/orders/" + orderId, null, token(900101, "USER")).body()).path("data");
        stock(orderId, finalOrder.path("status").asText().equals("SHIPPING") ? 1 : 2, 0);
    }

    private JsonNode createCod() throws Exception {
        var product = send("POST", "/api/products", Map.of("sku", "COD-" + UUID.randomUUID(),
                "name", "COD live fixture", "unitPrice", 12.50), token(900101, "ADMIN"));
        assertEquals(201, product.statusCode());
        long productId = json.readTree(product.body()).path("data").path("id").asLong();
        assertEquals(200, send("POST", "/api/inventory/stocks", Map.of("productId", productId, "quantity", 2,
                "idempotencyKey", UUID.randomUUID().toString()), token(900101, "ADMIN")).statusCode());
        var request = Map.of("channel", "WEB", "paymentMethod", "COD", "idempotencyKey", UUID.randomUUID().toString(),
                "items", List.of(Map.of("productId", productId, "quantity", 1)), "shippingAddress", Map.of(
                        "recipientName", "COD Fixture", "phone", "0901234567", "street", "Fixture street",
                        "ward", "Ward", "district", "District", "province", "Province"));
        var response = send("POST", "/api/orders", request, token(900101, "USER"));
        assertEquals(201, response.statusCode());
        JsonNode order = json.readTree(response.body()).path("data");
        assertEquals("CONFIRMED", order.path("status").asText());
        assertEquals("COD", order.path("paymentMethod").asText());
        assertEquals(200, send("POST", "/api/orders", request, token(900101, "USER")).statusCode());
        stock(order.path("id").asLong(), 1, 1);
        return order;
    }

    private Map<String, Object> receipt(JsonNode payment) {
        return Map.of("amount", payment.path("amount").decimalValue(), "idempotencyKey", UUID.randomUUID().toString(),
                "receiptNumber", "COD-" + UUID.randomUUID());
    }

    private Map<String, Object> shipment(long orderId) {
        return Map.of("carrier", "COD Fixture Carrier", "trackingNumber", "COD-" + orderId);
    }

    private void ship(long orderId) throws Exception {
        assertEquals(200, send("POST", "/api/orders/" + orderId + "/ship", shipment(orderId), token(900101, "ADMIN")).statusCode());
        assertEquals(200, send("POST", "/api/orders/" + orderId + "/ship", shipment(orderId), token(900101, "ADMIN")).statusCode());
    }

    private JsonNode await(String path, String status) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", path, null, token(900101, "USER"));
            assertTrue(response.statusCode() == 200 || response.statusCode() == 404,
                    "Unexpected polling HTTP status: " + response.statusCode());
            JsonNode result = json.readTree(response.body()).path("data");
            if (status.equals(result.path("status").asText())) {
                return result;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("COD transition timed out: " + status);
    }

    private void awaitShipment(long orderId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            if (scalar("SELECT count(*) FROM cod_deliveries WHERE order_id=? AND state='SHIPPED'", orderId) == 1) {
                return;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Payment did not acknowledge COD shipment");
    }

    private void awaitPaidOrder(long orderId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            JsonNode order = json.readTree(send("GET", "/api/orders/" + orderId, null, token(900101, "USER")).body()).path("data");
            if (!order.path("codPaidAt").isNull() && order.path("codPaymentId").asLong() > 0) {
                return;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("Order did not acknowledge COD collection");
    }

    private void awaitNotification(long orderId, String type) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        try (var connection = DriverManager.getConnection(System.getenv().getOrDefault("NOTIFICATION_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/notification_db"), "notification_user",
                System.getenv("NOTIFICATION_LIVE_DB_PASSWORD"));
             var statement = connection.prepareStatement("SELECT count(*) FROM notification_logs WHERE order_id=? AND event_type=?")) {
            assertEquals("notification_db", connection.getCatalog());
            statement.setLong(1, orderId);
            statement.setString(2, type);
            do {
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    if (result.getInt(1) == 1) {
                        return;
                    }
                }
                Thread.sleep(300);
            } while (Instant.now().isBefore(deadline));
        }
        throw new AssertionError("COD terminal notification timed out");
    }

    private void stock(long orderId, int available, int reserved) throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv().getOrDefault("ORDER_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/order_db"), "order_user", System.getenv("ORDER_LIVE_DB_PASSWORD"));
             var statement = connection.prepareStatement("SELECT available,reserved FROM inventory WHERE product_id="
                     + "(SELECT product_id FROM order_items WHERE order_id=?)")) {
            assertEquals("order_db", connection.getCatalog());
            statement.setLong(1, orderId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(available, result.getInt(1));
                assertEquals(reserved, result.getInt(2));
            }
        }
    }
}
