package com.app.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "vnpay_refunds")
public class VnpayRefund {

    @Id
    private Long paymentId;

    @Column(nullable = false, unique = true, length = 32)
    private String requestId;

    @Column(nullable = false)
    private Long requestedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column(nullable = false, length = 45)
    private String serverIp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VnpayRefundState state = VnpayRefundState.UNKNOWN;

    @Column(unique = true, length = 15)
    private String transactionNumber;

    @Column(length = 2)
    private String responseCode;

    private Instant checkedAt;

    protected VnpayRefund() {
    }

    public VnpayRefund(
            Long paymentId,
            String requestId,
            Long requestedBy,
            Instant requestedAt,
            String serverIp
    ) {
        this.paymentId = paymentId;
        this.requestId = requestId;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.serverIp = serverIp;
    }

    public void record(VnpayRefundState outcome, String transaction, String responseCode) {
        if (state == VnpayRefundState.COMPLETED) {
            return;
        }
        if (transactionNumber != null && transaction != null && !transactionNumber.equals(transaction)) {
            throw new IllegalStateException("Conflicting refund transaction");
        }
        if (transaction != null) {
            transactionNumber = transaction;
        }
        state = outcome;
        this.responseCode = responseCode;
        checkedAt = Instant.now();
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getServerIp() {
        return serverIp;
    }

    public VnpayRefundState getState() {
        return state;
    }

    public String getTransactionNumber() {
        return transactionNumber;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

}
