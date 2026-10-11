package com.app.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "inventory")
@Check(constraints = "available >= 0 AND reserved >= 0 AND product_id > 0")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Version
    private Long version;

    protected Inventory() {
    }

    public Inventory(
            Long productId,
            int available
    ) {
        if (productId == null || productId <= 0 || available < 0) {
            throw new IllegalArgumentException("Invalid initial stock");
        }
        this.productId = productId;
        this.available = available;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }
}
