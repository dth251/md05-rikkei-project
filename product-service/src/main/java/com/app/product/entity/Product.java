package com.app.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "products", indexes = @Index(name = "idx_product_parent", columnList = "parent_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_product_variant", columnNames = {"parent_id", "variant_key"}))
@Check(constraints = "(parent_id IS NULL AND variant_key IS NULL) OR "
        + "(parent_id IS NOT NULL AND variant_key IS NOT NULL AND parent_id <> id)")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_product_parent"))
    private Product parent;

    @Column(name = "variant_key", length = 64)
    private String variantKey;

    @ElementCollection
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attribute_name", length = 32)
    @Column(name = "attribute_value", nullable = false, length = 64)
    @BatchSize(size = 50)
    private Map<String, String> attributes = new LinkedHashMap<>();

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(
            String sku
    ) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(
            String name
    ) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description
    ) {
        this.description = description;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(
            BigDecimal unitPrice
    ) {
        this.unitPrice = unitPrice;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(
            String category
    ) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(
            String brand
    ) {
        this.brand = brand;
    }

    public boolean isActive() {
        return active;
    }

    public Long getParentId() {
        return parent == null ? null : parent.getId();
    }

    public Map<String, String> getAttributes() {
        return Map.copyOf(attributes);
    }

    public void defineVariant(
            Product parent,
            Map<String, String> attributes,
            String variantKey
    ) {
        if (id != null || this.parent != null) {
            throw new IllegalStateException("Variant identity is immutable");
        }
        this.parent = parent;
        this.attributes.putAll(attributes);
        this.variantKey = variantKey;
    }

    public void setActive(
            boolean active
    ) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
