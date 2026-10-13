package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.entity.PaymentMethod;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.service.CodPaymentService;
import com.app.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@Tag("live")
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "commerce.outbox.enabled=false",
        "payment.vnpay.enabled=false"
})
@Transactional
class CodProjectionPgLiveTests {

    @Autowired
    private PaymentService payments;
    @Autowired
    private CodPaymentService cod;
    @Autowired
    private PaymentRepository repository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv().getOrDefault("PAYMENT_LIVE_DB_URL",
                "jdbc:postgresql://localhost:15432/payment_db"));
        properties.add("spring.datasource.username", () -> "payment_user");
        properties.add("spring.datasource.password", () -> System.getenv("PAYMENT_LIVE_DB_PASSWORD"));
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void cancellationBeforeCreationAndReplaysUseRealPostgresInboxAndAdvisoryLock() {
        long orderId = orderId();
        CommerceEvent cancellation = event(orderId, "order.cod-cancelled");
        cod.accept(cancellation);
        cod.accept(cancellation);
        CommerceEvent creation = event(orderId, "order.cod-created");
        payments.acceptOrder(creation);
        payments.acceptOrder(creation);
        payments.acceptOrder(event(orderId, "order.cod-created"));
        var payment = repository.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentMethod.COD, payment.getPaymentMethod());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals(false, payment.isReservationExpired());
    }

    @Test
    void shipmentBeforeCreationKeepsCashPendingAndRejectsMethodChange() {
        long orderId = orderId();
        cod.accept(event(orderId, "order.cod-shipped"));
        payments.acceptOrder(event(orderId, "order.cod-created"));
        var payment = repository.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentMethod.COD, payment.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertThrows(IllegalArgumentException.class, () -> payments.acceptOrder(event(orderId, "order.created")));
    }

    @Test
    void databaseRejectsCodSuccessWithoutReceiptAudit() {
        long orderId = orderId();
        payments.acceptOrder(event(orderId, "order.cod-created"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE payments SET status='SUCCESS',completed_at=CURRENT_TIMESTAMP WHERE order_id=?", orderId));
    }

    private long orderId() {
        return 9000000000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1000000000L);
    }

    private CommerceEvent event(long orderId, String type) {
        return new CommerceEvent(UUID.randomUUID().toString(), type, orderId, 900101L, BigDecimal.TEN,
                null, Instant.now());
    }
}
