package com.app.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "payments")
@Check(constraints = "amount > 0 AND order_id > 0 AND user_id > 0")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public Payment(
            Long orderId,
            Long userId,
            BigDecimal amount
    ) {
        if (orderId == null || orderId <= 0 || userId == null || userId <= 0 || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid payment");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
    }

    public void complete(PaymentStatus outcome) {
        if (outcome == null || outcome == PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Final payment outcome required");
        }
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment already completed");
        }
        status = outcome;
        completedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
