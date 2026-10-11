package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import com.app.payment.repository.EventInbox;
import com.app.payment.repository.ExpiredOrderRepository;
import com.app.payment.repository.PaymentOrderLock;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.service.PaymentExpiryService;
import com.app.payment.service.PaymentService;
import com.app.payment.config.exception.ApiException;
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
class PaymentExpiryTests {

    private static final AtomicLong IDS = new AtomicLong(88000);

    @Autowired
    private PaymentExpiryService expiry;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private ExpiredOrderRepository expiredOrders;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private PaymentOrderLock locks;
    @MockitoBean
    private EventInbox inbox;

    @BeforeEach
    void acceptEvents() {
        when(inbox.claim(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void pendingPaymentExpiresOnceAndCannotBePaidBySimulation() {
        Payment payment = pending();
        expiry.accept(event(payment, "order.expired", null));
        expiry.accept(event(payment, "order.expired", null));
        Payment result = payments.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, result.getStatus());
        assertTrue(result.isReservationExpired());
        assertEquals(RefundStatus.NONE, result.getRefundStatus());
        assertThrows(ApiException.class, () -> paymentService.simulate(payment.getId(), PaymentStatus.SUCCESS));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='payment.failed' AND payload LIKE ?",
                Integer.class, "%\"paymentId\":" + payment.getId() + ",%"));
    }

    @Test
    void successfulPaymentRequiresRefundWhenExpiryArrivesLate() {
        Payment payment = pending();
        payment.complete(PaymentStatus.SUCCESS);
        payments.saveAndFlush(payment);
        expiry.accept(event(payment, "order.expired", null));
        expiry.accept(event(payment, "order.refund-requested", payment.getId()));
        Payment result = payments.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals(RefundStatus.REQUIRED, result.getRefundStatus());
    }

    @Test
    void expiryBeforeCreationKeepsMarkerAndConflictingRefundRollsBack() {
        long orderId = IDS.incrementAndGet();
        expiry.accept(new CommerceEvent(UUID.randomUUID().toString(), "order.expired", orderId, 881L,
                BigDecimal.TEN, null, Instant.now()));
        assertTrue(expiredOrders.existsById(orderId));
        Payment payment = payments.saveAndFlush(new Payment(orderId, 881L, BigDecimal.TEN));
        expiry.accept(event(payment, "order.expired", null));
        assertEquals(PaymentStatus.FAILED, payments.findById(payment.getId()).orElseThrow().getStatus());
        Payment another = pending();
        assertThrows(IllegalArgumentException.class, () -> expiry.accept(event(another, "order.refund-requested", another.getId())));
        assertFalse(expiredOrders.existsById(another.getOrderId()));
        assertEquals(PaymentStatus.PENDING, payments.findById(another.getId()).orElseThrow().getStatus());
    }

    private Payment pending() {
        return payments.saveAndFlush(new Payment(IDS.incrementAndGet(), 881L, BigDecimal.TEN));
    }

    private CommerceEvent event(Payment payment, String type, Long paymentId) {
        return new CommerceEvent(UUID.randomUUID().toString(), type, payment.getOrderId(), payment.getUserId(),
                payment.getAmount(), paymentId, Instant.now());
    }
}
