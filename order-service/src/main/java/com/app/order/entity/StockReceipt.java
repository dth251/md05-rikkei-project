package com.app.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stock_receipts")
public class StockReceipt {

    @Id
    @Column(length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    private int available;
    private int reserved;

    @Column(nullable = false)
    private Instant createdAt;

    protected StockReceipt() {
    }

    public StockReceipt(String idempotencyKey, Long productId, int quantity) {
        this.idempotencyKey = idempotencyKey;
        this.productId = productId;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public boolean matches(Long productId, int quantity) {
        return this.productId.equals(productId) && this.quantity == quantity;
    }

    public void snapshot(int available, int reserved) {
        this.available = available;
        this.reserved = reserved;
    }

    public Long getProductId() { return productId; }
    public int getAvailable() { return available; }
    public int getReserved() { return reserved; }
}
