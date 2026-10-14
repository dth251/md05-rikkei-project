package com.app.notification.entity;

import com.app.notification.dto.event.CommerceEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "notification_logs")
@Check(name = "ck_notification_terminal_identity", constraints = "order_id > 0 AND user_id > 0 AND amount > 0 AND "
        + "((event_type='order.expired' AND payment_id IS NULL) OR "
        + "(event_type IN ('order.confirmed','order.cancelled') AND payment_id IS NOT NULL AND payment_id > 0))")
public class NotificationLog {

    @Id
    @Column(length = 36)
    private String eventId;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column
    private Long paymentId;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected NotificationLog() {
    }

    public NotificationLog(CommerceEvent event) {
        eventId = event.eventId();
        orderId = event.orderId();
        userId = event.userId();
        eventType = event.type();
        amount = event.amount();
        paymentId = event.paymentId();
        occurredAt = event.occurredAt();
        createdAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEventType() {
        return eventType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
