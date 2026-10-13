package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live")
class ReturnRefundLiveTests extends FullOrderReturnLiveFixture {

    @Test
    void fullCodReturnRestocksAndRefundsOnceUnderConcurrentReplays() throws Exception {
        var order = completed(true);
        stock(order, 1);
        var refund = Map.of("amount", order.amount(), "idempotencyKey", UUID.randomUUID().toString(), "receiptNumber", "CASH-RETURN-" + UUID.randomUUID());
        String refundPath = "/api/payments/" + order.paymentId() + "/cod/refund";
        assertEquals(409, send("POST", refundPath, refund, token(900101, "ADMIN")).statusCode());
        approved(order);
        var receipt = Map.of("warehouseReceipt", "WAREHOUSE-" + UUID.randomUUID(), "restock", true);
        race(() -> send("POST", "/api/orders/" + order.orderId() + "/return/receive", receipt, token(900101, "ADMIN")).statusCode());
        awaitRefundRequired(order.paymentId());
        stock(order, 3);
        assertEquals(403, send("POST", refundPath, refund, token(900101, "USER")).statusCode());
        race(() -> send("POST", refundPath, refund, token(900101, "ADMIN")).statusCode());
        await("/api/orders/" + order.orderId() + "/return", "REFUNDED");
        assertEquals(409, send("POST", refundPath, Map.of("amount", order.amount(), "idempotencyKey", "changed",
                "receiptNumber", refund.get("receiptNumber")), token(900101, "ADMIN")).statusCode());
        var response = send("GET", refundPath, null, token(900101, "USER"));
        assertEquals(200, response.statusCode());
        assertEquals(900101, json.readTree(response.body()).path("data").path("refundedBy").asLong());
        assertEquals(404, send("GET", refundPath, null, token(900102, "USER")).statusCode());
        assertEquals(1, scalar("SELECT count(*) FROM cash_refunds WHERE payment_id=?", order.paymentId()));
        assertEquals(1, scalar("SELECT count(*) FROM outbox_events WHERE event_type='payment.return-refunded' AND payload LIKE ?",
                "%\"orderId\":" + order.orderId() + ",%"));
        stock(order, 3);
    }

    @Test
    void rejectedReturnLeavesStockAndMoneyUnchanged() throws Exception {
        var order = completed(true);
        String path = "/api/orders/" + order.orderId() + "/return";
        assertEquals(200, send("POST", path, Map.of("idempotencyKey", UUID.randomUUID().toString(), "reason", "Return whole order"), token(900101, "USER")).statusCode());
        assertEquals(200, send("POST", path + "/decision", Map.of("approved", false, "note", "Return rejected"), token(900101, "ADMIN")).statusCode());
        assertEquals(409, send("POST", path + "/receive", Map.of("warehouseReceipt", UUID.randomUUID().toString(), "restock", true), token(900101, "ADMIN")).statusCode());
        stock(order, 1);
        assertEquals(1, scalar("SELECT count(*) FROM payments WHERE id=? AND refund_status='NONE'", order.paymentId()));
    }

    @Test
    void receivedDamagedWholeOrderRefundsWithoutRestocking() throws Exception {
        var order = completed(true);
        approved(order);
        received(order, false);
        assertEquals(200, send("POST", "/api/payments/" + order.paymentId() + "/cod/refund",
                Map.of("amount", order.amount(), "idempotencyKey", UUID.randomUUID().toString(), "receiptNumber", UUID.randomUUID().toString()),
                token(900101, "ADMIN")).statusCode());
        await("/api/orders/" + order.orderId() + "/return", "REFUNDED");
        stock(order, 1);
    }

    @Test
    void fullLegacySimulatedOrderCanReturnAndSimulateRefundWithoutProviderCapture() throws Exception {
        var order = completed(false, true);
        approved(order);
        received(order, true);
        String path = "/api/payments/" + order.paymentId() + "/simulate-refund";
        assertEquals(200, send("POST", path, null, token(900101, "ADMIN")).statusCode());
        assertEquals(200, send("POST", path, null, token(900101, "ADMIN")).statusCode());
        await("/api/orders/" + order.orderId() + "/return", "REFUNDED");
        stock(order, 3);
    }

    private void race(Callable<Integer> task) throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(java.util.Collections.nCopies(24, task))) {
                assertEquals(200, result.get());
            }
        }
    }
}
