package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.app.order.dto.event.CommerceEvent;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.SalesChannel;
import com.app.order.repository.EventInbox;
import com.app.order.repository.OrderRepository;
import com.app.order.service.OrderPaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
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
class OrderSagaTests {

    private static final AtomicLong PRODUCT_IDS = new AtomicLong(700000);

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderPaymentService payments;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EventInbox inbox;

    @BeforeEach
    void claim() {
        when(inbox.claim(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void failedPaymentReturnsStockExactlyOnceEvenWithAnotherEventId() {
        PurchaseOrder order = pending(2);
        payments.accept(event(order, "payment.failed", order.getTotalAmount()));
        payments.accept(event(order, "payment.failed", order.getTotalAmount()));
        assertEquals(OrderStatus.CANCELLED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertStock(order, 2, 0);
        assertEquals(1, terminalEvents(order));
    }

    @Test
    void successfulPaymentConsumesReservedStockWithoutReturningAvailable() {
        PurchaseOrder order = pending(2);
        payments.accept(event(order, "payment.succeeded", order.getTotalAmount()));
        payments.accept(event(order, "payment.failed", order.getTotalAmount()));
        assertEquals(OrderStatus.CONFIRMED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertStock(order, 0, 0);
        assertEquals(1, terminalEvents(order));
    }

    @Test
    void mismatchedAmountDoesNotChangeOrderOrStock() {
        PurchaseOrder order = pending(2);
        assertThrows(IllegalArgumentException.class, () -> payments.accept(event(order, "payment.failed", BigDecimal.ONE)));
        assertEquals(OrderStatus.PENDING_PAYMENT, orders.findById(order.getId()).orElseThrow().getStatus());
        assertStock(order, 0, 2);
        assertEquals(0, terminalEvents(order));
    }

    @Test
    void inconsistentReservationRollsBackAndAllowsMessageRetry() {
        PurchaseOrder order = pending(1);
        assertThrows(IllegalStateException.class, () -> payments.accept(event(order, "payment.failed", order.getTotalAmount())));
        assertEquals(OrderStatus.PENDING_PAYMENT, orders.findById(order.getId()).orElseThrow().getStatus());
        assertStock(order, 0, 1);
        assertEquals(0, terminalEvents(order));
    }

    @Test
    void alreadyClaimedEventDoesNotApplyOutcome() {
        PurchaseOrder order = pending(2);
        when(inbox.claim(anyString(), anyString())).thenReturn(false);
        payments.accept(event(order, "payment.failed", order.getTotalAmount()));
        assertStock(order, 0, 2);
        assertEquals(OrderStatus.PENDING_PAYMENT, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    private PurchaseOrder pending(int reserved) {
        long product = PRODUCT_IDS.incrementAndGet();
        jdbc.update("INSERT INTO inventory (product_id, available, reserved, version) VALUES (?, 0, ?, 0)", product, reserved);
        PurchaseOrder order = new PurchaseOrder(800L, UUID.randomUUID().toString(), SalesChannel.WEB);
        order.addItem(product, "SAGA", "Snapshot", new BigDecimal("12.50"), 2);
        return orders.saveAndFlush(order);
    }

    private CommerceEvent event(PurchaseOrder order, String type, BigDecimal amount) {
        return new CommerceEvent(UUID.randomUUID().toString(), type, order.getId(), order.getUserId(),
                amount, 42L, Instant.now());
    }

    private void assertStock(PurchaseOrder order, int available, int reserved) {
        Long product = order.getItems().getFirst().getProductId();
        assertEquals(available, jdbc.queryForObject("SELECT available FROM inventory WHERE product_id=?", Integer.class, product));
        assertEquals(reserved, jdbc.queryForObject("SELECT reserved FROM inventory WHERE product_id=?", Integer.class, product));
    }

    private int terminalEvents(PurchaseOrder order) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type IN ('order.confirmed', 'order.cancelled') AND payload LIKE ?",
                Integer.class, "%\"orderId\":" + order.getId() + ",%");
    }
}
