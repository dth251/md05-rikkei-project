package com.app.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cash_refunds")
public class CashRefund {

    @Id
    private Long paymentId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, unique = true, length = 128)
    private String receiptNumber;

    @Column(nullable = false)
    private Long refundedBy;

    @Column(nullable = false)
    private Instant refundedAt;

    protected CashRefund() {
    }

    public CashRefund(
            Long paymentId,
            BigDecimal amount,
            String key,
            String receipt,
            Long administratorId
    ) {
        this.paymentId = paymentId;
        this.amount = amount;
        idempotencyKey = key;
        receiptNumber = receipt;
        refundedBy = administratorId;
        refundedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public boolean matches(
            String key,
            String receipt,
            Long administratorId
    ) {
        return idempotencyKey.equals(key) && receiptNumber.equals(receipt) && refundedBy.equals(administratorId);
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public Long getRefundedBy() {
        return refundedBy;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

}
