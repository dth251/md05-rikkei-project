package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.app.payment.security.VnpaySignature;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

class FullOrderReturnLiveFixture extends PaymentLiveFixture {

    record CompletedOrder(long orderId, long paymentId, long productId, BigDecimal amount) {
    }

    void ready() throws Exception {
        for (String path : List.of("/api/orders/-1", "/api/payments/-1")) {
            Instant deadline = Instant.now().plusSeconds(45);
            int code;
            do {
                code = send("GET", path, null, token(900101, "ADMIN")).statusCode();
                if (code == 404) {
                    break;
                }
                assertTrue(code == 503 || code == 504, "Unexpected readiness status: " + code);
                Thread.sleep(300);
            } while (Instant.now().isBefore(deadline));
            assertEquals(404, code);
        }
    }

    CompletedOrder completed(boolean cod) throws Exception {
        return completed(cod, false);
    }

    CompletedOrder completed(boolean cod, boolean simulated) throws Exception {
        ready();
        var product = send("POST", "/api/products", Map.of("sku", "RETURN-" + UUID.randomUUID(),
                "name", "Whole order return fixture", "unitPrice", 12.50), token(900101, "ADMIN"));
        assertEquals(201, product.statusCode());
        long productId = json.readTree(product.body()).path("data").path("id").asLong();
        assertEquals(200, send("POST", "/api/inventory/stocks", Map.of("productId", productId, "quantity", 3,
                "idempotencyKey", UUID.randomUUID().toString()), token(900101, "ADMIN")).statusCode());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("channel", "WEB");
        request.put("paymentMethod", cod ? "COD" : "ONLINE");
        request.put("idempotencyKey", UUID.randomUUID().toString());
        request.put("items", List.of(Map.of("productId", productId, "quantity", 2)));
        request.put("shippingAddress", Map.of("recipientName", "Return Fixture", "phone", "0901234567",
                "street", "Street", "ward", "Ward", "district", "District", "province", "Province"));
        var response = send("POST", "/api/orders", request, token(900101, "USER"));
        assertEquals(201, response.statusCode());
        long orderId = json.readTree(response.body()).path("data").path("id").asLong();
        JsonNode payment = await("/api/payments/by-order/" + orderId, "PENDING");
        long paymentId = payment.path("id").asLong();
        if (!cod) {
            if (simulated) {
                assertEquals(200, simulate(paymentId, "SUCCESS", "ADMIN").statusCode());
            } else {
                captureVnpay(payment);
            }
            await("/api/orders/" + orderId, "CONFIRMED");
        }
        assertEquals(200, send("POST", "/api/orders/" + orderId + "/ship",
                Map.of("carrier", "Return Carrier", "trackingNumber", "RETURN-" + orderId), token(900101, "ADMIN")).statusCode());
        if (cod) {
            var collection = Map.of("amount", payment.path("amount").decimalValue(), "idempotencyKey", UUID.randomUUID().toString(),
                    "receiptNumber", "COLLECT-" + UUID.randomUUID());
            eventuallyPost("/api/payments/" + paymentId + "/cod/collect", collection);
        }
        eventuallyPost("/api/orders/" + orderId + "/complete", null);
        await("/api/orders/" + orderId, "COMPLETED");
        return new CompletedOrder(orderId, paymentId, productId, payment.path("amount").decimalValue());
    }

    void approved(CompletedOrder order) throws Exception {
        assertEquals(200, send("POST", "/api/orders/" + order.orderId() + "/return",
                Map.of("idempotencyKey", UUID.randomUUID().toString(), "reason", "Return the entire order"),
                token(900101, "USER")).statusCode());
        assertEquals(200, send("POST", "/api/orders/" + order.orderId() + "/return/decision",
                Map.of("approved", true, "note", "All goods approved for return"), token(900101, "ADMIN")).statusCode());
    }

    void received(CompletedOrder order, boolean restock) throws Exception {
        assertEquals(200, send("POST", "/api/orders/" + order.orderId() + "/return/receive",
                Map.of("warehouseReceipt", "WAREHOUSE-" + UUID.randomUUID(), "restock", restock), token(900101, "ADMIN")).statusCode());
        awaitRefundRequired(order.paymentId());
    }

    void awaitRefundRequired(long paymentId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        do {
            JsonNode payment = json.readTree(send("GET", "/api/payments/" + paymentId, null, token(900101, "USER")).body()).path("data");
            if (payment.path("refundStatus").asText().equals("REQUIRED")) {
                return;
            }
            Thread.sleep(300);
        } while (Instant.now().isBefore(deadline));
        fail("Payment did not acknowledge returned goods");
    }

    JsonNode await(String path, String status) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        do {
            var response = send("GET", path, null, token(900101, "USER"));
            assertTrue(response.statusCode() == 200 || response.statusCode() == 404, "Polling status: " + response.statusCode());
            JsonNode data = json.readTree(response.body()).path("data");
            if (status.equals(data.path("status").asText())) {
                return data;
            }
            Thread.sleep(300);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Return transition timed out: " + status);
    }

    void eventuallyPost(String path, Object body) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        int code;
        do {
            code = send("POST", path, body, token(900101, "ADMIN")).statusCode();
            if (code == 200) {
                return;
            }
            assertEquals(409, code, "Only the asynchronous acknowledgement may delay this transition");
            Thread.sleep(300);
        } while (Instant.now().isBefore(deadline));
        assertEquals(200, code);
    }

    void stock(CompletedOrder order, int available) throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv().getOrDefault("ORDER_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/order_db"), "order_user", System.getenv("ORDER_LIVE_DB_PASSWORD"));
             var statement = connection.prepareStatement("SELECT available,reserved FROM inventory WHERE product_id=?")) {
            assertEquals("order_db", connection.getCatalog());
            statement.setLong(1, order.productId());
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(available, result.getInt(1));
                assertEquals(0, result.getInt(2));
            }
        }
    }

    private void captureVnpay(JsonNode payment) throws Exception {
        var checkout = send("POST", "/api/payments/" + payment.path("id").asLong() + "/vnpay/checkout", null, token(900101, "USER"));
        assertEquals(200, checkout.statusCode());
        String reference = json.readTree(checkout.body()).path("data").path("reference").asText();
        var parameters = new TreeMap<String, String>();
        parameters.put("vnp_TmnCode", "FIXTURE1");
        parameters.put("vnp_TxnRef", reference);
        parameters.put("vnp_Amount", payment.path("amount").decimalValue().movePointRight(2).toBigIntegerExact().toString());
        parameters.put("vnp_ResponseCode", "00");
        parameters.put("vnp_TransactionStatus", "00");
        parameters.put("vnp_TransactionNo", Long.toString(200000000L + payment.path("id").asLong()));
        parameters.put("vnp_PayDate", DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.now()));
        String query = VnpaySignature.canonicalQuery(parameters);
        var request = HttpRequest.newBuilder(URI.create(gateway + "/api/payments/vnpay/ipn?" + query
                + "&vnp_SecureHash=" + VnpaySignature.hmac(query, System.getenv("VNPAY_LIVE_HASH_SECRET"))))
                .timeout(java.time.Duration.ofSeconds(20)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("00", json.readTree(response.body()).path("RspCode").asText());
    }
}
