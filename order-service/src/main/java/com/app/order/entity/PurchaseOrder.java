package com.app.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "orders", indexes = @Index(name = "idx_order_expiration", columnList = "status,createdAt"), uniqueConstraints = @UniqueConstraint(
        name = "uk_order_user_idempotency", columnNames = {"user_id", "idempotency_key"}
))
@Check(constraints = "total_amount >= 0 AND user_id > 0")
@Check(name = "ck_order_fulfillment_v1", constraints = "(status NOT IN ('SHIPPING','COMPLETED') "
        + "AND carrier IS NULL AND tracking_number IS NULL AND shipped_at IS NULL AND completed_at IS NULL) OR "
        + "(status IN ('SHIPPING','COMPLETED') AND carrier IS NOT NULL AND tracking_number IS NOT NULL "
        + "AND shipped_at IS NOT NULL AND shipping_recipient IS NOT NULL AND shipping_phone IS NOT NULL "
        + "AND shipping_street IS NOT NULL AND shipping_ward IS NOT NULL AND shipping_district IS NOT NULL "
        + "AND shipping_province IS NOT NULL AND ((status='SHIPPING' AND completed_at IS NULL) OR "
        + "(status='COMPLETED' AND completed_at IS NOT NULL AND completed_at>=shipped_at)))")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.ONLINE;

    @Column(name = "cod_payment_id")
    private Long codPaymentId;

    @Column(name = "cod_paid_at")
    private Instant codPaidAt;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(length = 100)
    private String carrier;

    @Column(name = "tracking_number", length = 128)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Version
    private Long version;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(
            Long userId,
            String idempotencyKey,
            SalesChannel channel
    ) {
        if (userId == null || userId <= 0 || idempotencyKey == null
                || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Valid user and idempotency key required");
        }
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.channel = Objects.requireNonNull(channel);
    }

    public void addItem(
            Long productId,
            String sku,
            String name,
            BigDecimal unitPrice,
            int quantity
    ) {
        items.add(new OrderItem(this, productId, sku, name, unitPrice, quantity));
        totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public SalesChannel getChannel() {
        return channel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public void completePayment(boolean successful) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order already finalized");
        }
        status = successful ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Long getCodPaymentId() {
        return codPaymentId;
    }

    public Instant getCodPaidAt() {
        return codPaidAt;
    }

    public void confirmCod() {
        if (status != OrderStatus.PENDING_PAYMENT || shippingAddress == null) {
            throw new IllegalStateException("COD requires a pending order with shipping address");
        }
        paymentMethod = PaymentMethod.COD;
        status = OrderStatus.CONFIRMED;
    }

    public void cancelCod() {
        if (paymentMethod != PaymentMethod.COD || status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only unshipped COD orders can cancel");
        }
        status = OrderStatus.CANCELLED;
    }

    public void recordCodPayment(Long paymentId, Instant paidAt) {
        if (paymentMethod != PaymentMethod.COD || status != OrderStatus.SHIPPING
                || paymentId == null || paymentId <= 0 || paidAt == null || codPaymentId != null) {
            throw new IllegalStateException("COD collection requires an unpaid shipped order");
        }
        codPaymentId = paymentId;
        codPaidAt = paidAt;
    }

    public void expire() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending reservations can expire");
        }
        status = OrderStatus.EXPIRED;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }
    public String getCarrier() {
        return carrier;
    }
    public String getTrackingNumber() {
        return trackingNumber;
    }
    public Instant getShippedAt() {
        return shippedAt;
    }
    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setShippingAddress(
            ShippingAddress address
    ) {
        if (shippingAddress != null && !shippingAddress.equals(address)) {
            throw new IllegalStateException("Shipping address snapshot is immutable");
        }
        shippingAddress = address;
    }

    public void ship(
            String carrier,
            String trackingNumber,
            Instant now
    ) {
        if (status != OrderStatus.CONFIRMED || shippingAddress == null) {
            throw new IllegalStateException("Only confirmed orders with delivery address can ship");
        }
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        shippedAt = now;
        status = OrderStatus.SHIPPING;
    }

    public void complete(
            Instant now
    ) {
        if (status != OrderStatus.SHIPPING || (paymentMethod == PaymentMethod.COD && codPaidAt == null)) {
            throw new IllegalStateException("Only shipped orders can complete");
        }
        completedAt = now.isBefore(shippedAt) ? shippedAt : now;
        status = OrderStatus.COMPLETED;
    }
}
