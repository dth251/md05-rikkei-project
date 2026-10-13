package com.app.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_returns")
public class OrderReturn {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(nullable = false)
    private Instant requestedAt;

    private Long decidedBy;
    private Instant decidedAt;

    @Column(length = 500)
    private String decisionNote;

    private Long receivedBy;
    private Instant receivedAt;

    @Column(length = 128, unique = true)
    private String warehouseReceipt;

    private Boolean restock;
    private Long paymentId;
    private Instant refundedAt;

    protected OrderReturn() {
    }

    public OrderReturn(
            PurchaseOrder order,
            String idempotencyKey,
            String reason
    ) {
        orderId = order.getId();
        userId = order.getUserId();
        amount = order.getTotalAmount();
        this.idempotencyKey = idempotencyKey;
        this.reason = reason;
        requestedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public void decide(
            boolean approved,
            String note,
            Long administratorId
    ) {
        if (status != ReturnStatus.REQUESTED) {
            throw new IllegalStateException("Return request already decided");
        }
        status = approved ? ReturnStatus.APPROVED : ReturnStatus.REJECTED;
        decidedBy = administratorId;
        decidedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        decisionNote = note;
    }

    public void receive(
            String receipt,
            boolean restock,
            Long administratorId
    ) {
        if (status != ReturnStatus.APPROVED) {
            throw new IllegalStateException("Only approved returns can receive goods");
        }
        warehouseReceipt = receipt;
        this.restock = restock;
        receivedBy = administratorId;
        receivedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        status = ReturnStatus.RECEIVED;
    }

    public void acknowledgeRefund(
            Long paymentId,
            boolean completed,
            Instant occurredAt
    ) {
        if (status != ReturnStatus.RECEIVED && status != ReturnStatus.REFUND_PENDING && status != ReturnStatus.REFUNDED) {
            throw new IllegalArgumentException("Refund acknowledgement requires returned goods");
        }
        if (paymentId == null || paymentId <= 0 || (this.paymentId != null && !this.paymentId.equals(paymentId))) {
            throw new IllegalArgumentException("Return payment identity mismatch");
        }
        this.paymentId = paymentId;
        if (status == ReturnStatus.REFUNDED) {
            return;
        }
        status = completed ? ReturnStatus.REFUNDED : ReturnStatus.REFUND_PENDING;
        if (completed) {
            refundedAt = occurredAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReason() {
        return reason;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Long getReceivedBy() {
        return receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getWarehouseReceipt() {
        return warehouseReceipt;
    }

    public Boolean getRestock() {
        return restock;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

}
