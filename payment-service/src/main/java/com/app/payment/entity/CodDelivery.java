package com.app.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cod_deliveries")
public class CodDelivery {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CodDeliveryState state;

    @Column(nullable = false)
    private Instant occurredAt;

    protected CodDelivery() {
    }

    public CodDelivery(
            Long orderId,
            Long userId,
            BigDecimal amount,
            CodDeliveryState state,
            Instant occurredAt
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.state = state;
        this.occurredAt = occurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public CodDeliveryState getState() {
        return state;
    }
}
