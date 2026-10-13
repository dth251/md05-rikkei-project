package com.app.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "returned_orders")
public class ReturnedOrder {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(nullable = false, unique = true)
    private Long paymentId;
    private Instant refundReadyAt;
    private Instant refundNotifiedAt;

    protected ReturnedOrder() {
    }

    public ReturnedOrder(
            Long orderId,
            Long userId,
            BigDecimal amount,
            Long paymentId,
            Instant receivedAt
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.paymentId = paymentId;
        this.receivedAt = receivedAt;
    }

    public boolean matches(
            Long userId,
            BigDecimal amount,
            Long paymentId
    ) {
        return this.userId.equals(userId) && this.amount.compareTo(amount) == 0
                && (this.paymentId == null || this.paymentId.equals(paymentId));
    }

    public boolean markReady() {
        if (refundReadyAt != null) {
            return false;
        }
        refundReadyAt = Instant.now();
        return true;
    }

    public boolean markRefunded() {
        if (refundNotifiedAt != null) {
            return false;
        }
        refundNotifiedAt = Instant.now();
        return true;
    }
}
