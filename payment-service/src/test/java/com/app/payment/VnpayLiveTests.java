package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.app.payment.security.VnpaySignature;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "VNPAY_LIVE_TEST", matches = "true")
class VnpayLiveTests extends PaymentLiveFixture {

    @Test
    void gatewaySuccessAndDuplicateCallbackConfirmOrderAndConsumeStockOnce() throws Exception {
        JsonNode payment = createPending();
        String callback = callback(payment, true);
        assertEquals("00", ipn(callback));
        assertEquals("02", ipn(callback));
        awaitOrder(payment, "CONFIRMED");
        assertOutcome(payment.path("orderId").asLong(), "payment.succeeded");
        assertStock(payment, 0);
    }

    @Test
    void gatewayFailedCallbackCancelsOrderAndReturnsStockOnce() throws Exception {
        JsonNode payment = createPending();
        String callback = callback(payment, false);
        assertEquals("00", ipn(callback));
        assertEquals("02", ipn(callback));
        awaitOrder(payment, "CANCELLED");
        assertOutcome(payment.path("orderId").asLong(), "payment.failed");
        assertStock(payment, 1);
    }

    @Test
    void concurrentPostgresCallbacksPersistExactlyOneOutcome() throws Exception {
        JsonNode payment = createPending();
        String callback = callback(payment, true);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int index = 0; index < 6; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return ipn(callback);
                }));
            }
            start.countDown();
            int applied = 0;
            for (var result : results) {
                String code = result.get(30, java.util.concurrent.TimeUnit.SECONDS);
                assertTrue("00".equals(code) || "02".equals(code));
                if ("00".equals(code)) {
                    applied++;
                }
            }
            assertEquals(1, applied);
        }
        awaitOrder(payment, "CONFIRMED");
        assertOutcome(payment.path("orderId").asLong(), "payment.succeeded");
        assertStock(payment, 0);
    }

    @Test
    void lateCaptureRetainsRefundRequirementWithoutRevivingExpiredOrder() throws Exception {
        JsonNode payment = createPending();
        String callback = callback(payment, true);
        // Only this test's fresh order is aged; never alter shared TTL or real orders.
        try (var connection = orderDatabase(); var statement = connection.prepareStatement(
                "UPDATE orders SET created_at=? WHERE id=? AND status='PENDING_PAYMENT'")) {
            statement.setTimestamp(1, java.sql.Timestamp.from(Instant.now().minusSeconds(3600)));
            statement.setLong(2, payment.path("orderId").asLong());
            assertEquals(1, statement.executeUpdate());
        }
        awaitOrder(payment, "EXPIRED");
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline) && scalar(
                "SELECT count(*) FROM payments WHERE id=? AND reservation_expired=true", payment.path("id").asLong()) != 1) {
            Thread.sleep(300);
        }
        assertEquals("00", ipn(callback));
        assertEquals("02", ipn(callback));
        assertEquals(1, scalar("SELECT count(*) FROM payments WHERE id=? AND status='SUCCESS' AND refund_status='REQUIRED'",
                payment.path("id").asLong()));
        assertEquals(0, scalar("SELECT count(*) FROM outbox_events WHERE event_type='payment.succeeded' AND payload LIKE ?",
                "%\"orderId\":" + payment.path("orderId").asLong() + ",%"));
        awaitOrder(payment, "EXPIRED");
        assertStock(payment, 1);
    }

    private String callback(
            JsonNode payment,
            boolean success
    ) throws Exception {
        var checkout = send("POST", "/api/payments/" + payment.path("id").asLong() + "/vnpay/checkout",
                null, token(900101, "USER"));
        assertEquals(200, checkout.statusCode());
        String paymentUrl = json.readTree(checkout.body()).path("data").path("checkoutUrl").asText();
        Map<String, String> checkoutParameters = new TreeMap<>();
        for (String pair : URI.create(paymentUrl).getRawQuery().split("&")) {
            String[] entry = pair.split("=", 2);
            checkoutParameters.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
        }
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_TmnCode", checkoutParameters.get("vnp_TmnCode"));
        parameters.put("vnp_TxnRef", checkoutParameters.get("vnp_TxnRef"));
        parameters.put("vnp_Amount", checkoutParameters.get("vnp_Amount"));
        parameters.put("vnp_ResponseCode", success ? "00" : "24");
        parameters.put("vnp_TransactionStatus", success ? "00" : "02");
        parameters.put("vnp_TransactionNo", success ? Long.toString(100000000L + payment.path("id").asLong()) : "0");
        parameters.put("vnp_PayDate", DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.now()));
        String query = VnpaySignature.canonicalQuery(parameters);
        String secret = System.getenv("VNPAY_LIVE_HASH_SECRET");
        assertNotNull(secret, "Private fixture signing key required");
        return query + "&vnp_SecureHash=" + VnpaySignature.hmac(query, secret);
    }

    private String ipn(
            String query
    ) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(URI.create(gateway + "/api/payments/vnpay/ipn?" + query))
                .timeout(java.time.Duration.ofSeconds(20)).GET().build();
        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("api-gateway", response.headers().firstValue("X-Gateway").orElseThrow());
        return json.readTree(response.body()).path("RspCode").asText();
    }

    private void awaitOrder(
            JsonNode payment,
            String expected
    ) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline)) {
            var response = send("GET", "/api/orders/" + payment.path("orderId").asLong(), null, token(900101, "USER"));
            assertEquals(200, response.statusCode());
            if (expected.equals(json.readTree(response.body()).path("data").path("status").asText())) {
                return;
            }
            Thread.sleep(300);
        }
        fail("Order did not reach " + expected);
    }

    private void assertStock(
            JsonNode payment,
            int available
    ) throws Exception {
        long product;
        try (var connection = orderDatabase(); var statement = connection.prepareStatement(
                "SELECT product_id FROM order_items WHERE order_id=?")) {
            statement.setLong(1, payment.path("orderId").asLong());
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                product = rows.getLong(1);
            }
        }
        var response = send("GET", "/api/inventory/" + product, null, token(900101, "USER"));
        assertEquals(200, response.statusCode());
        var stock = json.readTree(response.body()).path("data");
        assertEquals(available, stock.path("available").asInt());
        assertEquals(0, stock.path("reserved").asInt());
    }

    private java.sql.Connection orderDatabase() throws Exception {
        return DriverManager.getConnection(System.getenv().getOrDefault("ORDER_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/order_db"), "order_user", System.getenv("ORDER_LIVE_DB_PASSWORD"));
    }
}
