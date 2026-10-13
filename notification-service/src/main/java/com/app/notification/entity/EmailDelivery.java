package com.app.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "email_deliveries", uniqueConstraints =
        @UniqueConstraint(name = "uq_email_order_stage", columnNames = {"order_id", "event_type"}))
public class EmailDelivery {

    @Id
    @Column(length = 36)
    private String deliveryId;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(nullable = false)
    private Long userId;
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;
    @Column(nullable = false, length = 12)
    private String state;
    @Column(nullable = false)
    private int attempts;
    @Column(length = 40)
    private String errorCode;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private Instant sentAt;

    protected EmailDelivery() {
    }
}
