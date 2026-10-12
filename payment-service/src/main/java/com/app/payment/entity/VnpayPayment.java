package com.app.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "vnpay_payments")
public class VnpayPayment {

    @Id
    private Long paymentId;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(nullable = false, length = 12)
    private String amountMinor;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 45)
    private String clientIp;

    @Column(nullable = false, length = 2048)
    private String checkoutUrl;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean failed;

    @Column(unique = true, length = 15)
    private String transactionNumber;

    private Instant capturedAt;

    protected VnpayPayment() {
    }

    public VnpayPayment(
            Long paymentId,
            String reference,
            String amountMinor,
            Instant createdAt,
            Instant expiresAt,
            String clientIp,
            String checkoutUrl
    ) {
        this.paymentId = paymentId;
        this.reference = reference;
        this.amountMinor = amountMinor;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.clientIp = clientIp;
        this.checkoutUrl = checkoutUrl;
    }

    public void capture(String transactionNumber, Instant capturedAt) {
        if (this.transactionNumber != null) {
            throw new IllegalStateException("Provider capture already recorded");
        }
        this.transactionNumber = transactionNumber;
        this.capturedAt = capturedAt;
    }

    public void fail() {
        failed = true;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getReference() {
        return reference;
    }

    public String getAmountMinor() {
        return amountMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public boolean isFailed() {
        return failed;
    }

    public String getTransactionNumber() {
        return transactionNumber;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
