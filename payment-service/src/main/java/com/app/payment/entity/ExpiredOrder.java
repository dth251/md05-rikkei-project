package com.app.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "expired_orders")
public class ExpiredOrder {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant createdAt;

    protected ExpiredOrder() {
    }

    public ExpiredOrder(Long orderId, Long userId, BigDecimal amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public boolean matches(Long userId, BigDecimal amount) {
        return this.userId.equals(userId) && this.amount.compareTo(amount) == 0;
    }
}
