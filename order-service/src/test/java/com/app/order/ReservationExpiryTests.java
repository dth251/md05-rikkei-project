package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.app.order.dto.event.CommerceEvent;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.SalesChannel;
import com.app.order.repository.EventInbox;
import com.app.order.repository.OrderRepository;
import com.app.order.service.OrderPaymentService;
import com.app.order.service.ProductCatalog;
import com.app.order.service.ReservationExpiryService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ReservationExpiryTests {

    private static final AtomicLong IDS = new AtomicLong(80000);

    @Autowired
    private ReservationExpiryService expiry;
    @Autowired
    private OrderPaymentService payments;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private ProductCatalog catalog;
    @MockitoBean
    private EventInbox inbox;

    @BeforeEach
    void acceptEvents() {
        when(inbox.claim(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void expiresOnceAndLatePaymentRequestsRefundWithoutTouchingStock() {
        PurchaseOrder order = reserved(true, 3);
        assertTrue(expiry.expire(order.getId(), cutoff()));
        assertFalse(expiry.expire(order.getId(), cutoff()));
        assertStock(order, 3, 0);
        assertEquals(OrderStatus.EXPIRED, orders.findById(order.getId()).orElseThrow().getStatus());
        CommerceEvent paid = paid(order);
        payments.accept(paid);
        when(inbox.claim(paid.eventId(), paid.type())).thenReturn(false);
        payments.accept(paid);
        assertStock(order, 3, 0);
        assertEquals(1, count(order, "order.expired"));
        assertEquals(1, count(order, "order.refund-requested"));
    }

    @Test
    void freshReservationIsNotExpiredAndFailedExpiryRollsBack() {
        PurchaseOrder fresh = reserved(false, 3);
        assertFalse(expiry.expire(fresh.getId(), cutoff()));
        assertStock(fresh, 0, 3);
        PurchaseOrder inconsistent = reserved(true, 2);
        assertThrows(IllegalStateException.class, () -> expiry.expire(inconsistent.getId(), cutoff()));
        assertEquals(OrderStatus.PENDING_PAYMENT, orders.findById(inconsistent.getId()).orElseThrow().getStatus());
        assertStock(inconsistent, 0, 2);
        assertEquals(0, count(inconsistent, "order.expired"));
    }

    @Test
    void concurrentSuccessAndExpirySerializeOnOrderLock() throws Exception {
        PurchaseOrder order = reserved(true, 3);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var expire = executor.submit(() -> { start.await(); return expiry.expire(order.getId(), cutoff()); });
            var pay = executor.submit(() -> { start.await(); payments.accept(paid(order)); return true; });
            start.countDown();
            expire.get();
            pay.get();
        }
        OrderStatus state = orders.findById(order.getId()).orElseThrow().getStatus();
        assertEquals(OrderStatus.EXPIRED, state);
        assertStock(order, 3, 0);
        assertEquals(1, count(order, "order.expired"));
        assertEquals(0, count(order, "order.confirmed"));
        assertEquals(1, count(order, "order.refund-requested"));
    }

    @Test
    void overduePaymentExpiresEvenWhenSchedulerHasNotScanned() {
        PurchaseOrder order = reserved(true, 3);
        payments.accept(paid(order));
        assertEquals(OrderStatus.EXPIRED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertStock(order, 3, 0);
        assertEquals(1, count(order, "order.expired"));
        assertEquals(1, count(order, "order.refund-requested"));
        assertEquals(0, count(order, "order.confirmed"));
        assertFalse(expiry.expire(order.getId(), cutoff()));
    }

    @Test
    void paymentBeforeDeadlineConfirmsAndLaterScanCannotReleaseStock() {
        PurchaseOrder order = reserved(false, 3);
        payments.accept(paid(order));
        assertEquals(OrderStatus.CONFIRMED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertFalse(expiry.expire(order.getId(), Instant.now().plusSeconds(1)));
        assertStock(order, 0, 0);
        assertEquals(1, count(order, "order.confirmed"));
        assertEquals(0, count(order, "order.expired"));
    }

    private PurchaseOrder reserved(boolean old, int reserved) {
        long productId = IDS.incrementAndGet();
        jdbc.update("INSERT INTO inventory(product_id,available,reserved,version) VALUES(?,0,?,0)", productId, reserved);
        PurchaseOrder order = new PurchaseOrder(801L, UUID.randomUUID().toString(), SalesChannel.WEB);
        order.addItem(productId, "EXPIRY", "Expiry", BigDecimal.TEN, 3);
        order = orders.saveAndFlush(order);
        if (old) {
            jdbc.update("UPDATE orders SET created_at=? WHERE id=?",
                    Timestamp.from(Instant.now().minusSeconds(301)), order.getId());
        }
        return order;
    }

    private Instant cutoff() { return Instant.now().minusSeconds(300); }

    private CommerceEvent paid(PurchaseOrder order) {
        return new CommerceEvent(UUID.randomUUID().toString(), "payment.succeeded", order.getId(), 801L,
                new BigDecimal("30.00"), 77L, Instant.now());
    }

    private int count(PurchaseOrder order, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type=? AND payload LIKE ?",
                Integer.class, type, "%\"orderId\":" + order.getId() + ",%");
    }

    private void assertStock(PurchaseOrder order, int available, int reserved) {
        Long productId = jdbc.queryForObject("SELECT product_id FROM order_items WHERE order_id=?", Long.class, order.getId());
        assertEquals(available, jdbc.queryForObject("SELECT available FROM inventory WHERE product_id=?", Integer.class, productId));
        assertEquals(reserved, jdbc.queryForObject("SELECT reserved FROM inventory WHERE product_id=?", Integer.class, productId));
    }
}
