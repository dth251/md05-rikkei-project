package com.app.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "order_items")
@Check(constraints = "quantity > 0 AND unit_price > 0 AND product_id > 0")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    OrderItem(
            PurchaseOrder purchaseOrder,
            Long productId,
            String sku,
            String name,
            BigDecimal unitPrice,
            int quantity
    ) {
        if (productId == null || productId <= 0 || sku == null || sku.isBlank()
                || name == null || name.isBlank() || unitPrice == null
                || unitPrice.signum() <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Invalid order item snapshot");
        }
        this.purchaseOrder = purchaseOrder;
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
