package com.app.auth.entity;

import com.app.auth.dto.request.AddressRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "addresses", indexes = @Index(name = "idx_address_owner", columnList = "user_id"))
@Check(constraints = "default_user_key IS NULL OR default_user_key = user_id")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false, length = 16)
    private String phone;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 100)
    private String ward;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(length = 10)
    private String postalCode;

    @Column(name = "default_user_key", unique = true)
    private Long defaultUserKey;

    protected Address() {
    }

    public Address(
            User user,
            AddressRequest request,
            boolean makeDefault
    ) {
        this.user = user;
        replace(request, makeDefault);
    }

    public void replace(
            AddressRequest request,
            boolean makeDefault
    ) {
        recipientName = request.recipientName().strip();
        phone = request.phone();
        street = request.street().strip();
        ward = request.ward().strip();
        district = request.district().strip();
        province = request.province().strip();
        postalCode = request.postalCode() == null ? null : request.postalCode().strip();
        makeDefault(makeDefault);
    }

    public void makeDefault(boolean value) {
        defaultUserKey = value ? user.getId() : null;
    }

    public boolean isDefaultAddress() {
        return defaultUserKey != null;
    }

    public Long getId() {
        return id;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public String getStreet() {
        return street;
    }

    public String getWard() {
        return ward;
    }

    public String getDistrict() {
        return district;
    }

    public String getProvince() {
        return province;
    }

    public String getPostalCode() {
        return postalCode;
    }
}
