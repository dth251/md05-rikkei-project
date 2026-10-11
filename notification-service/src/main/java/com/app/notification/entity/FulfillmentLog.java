package com.app.notification.entity;

import com.app.notification.dto.event.CommerceEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "fulfillment_logs", uniqueConstraints = @UniqueConstraint(
        name = "uk_fulfillment_order_stage", columnNames = {"order_id", "event_type"}))
@Check(constraints = "order_id > 0 AND user_id > 0 AND amount > 0 "
        + "AND event_type IN ('order.shipped','order.completed')")
public class FulfillmentLog {

    @Id
    @Column(length = 36)
    private String eventId;
    @Column(nullable = false)
    private Long orderId;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 40)
    private String eventType;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private Instant occurredAt;
    @Column(nullable = false)
    private Instant createdAt;

    protected FulfillmentLog() {
    }

    public FulfillmentLog(
            CommerceEvent event
    ) {
        eventId = event.eventId();
        orderId = event.orderId();
        userId = event.userId();
        eventType = event.type();
        amount = event.amount();
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
    public Instant getOccurredAt() {
        return occurredAt;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
