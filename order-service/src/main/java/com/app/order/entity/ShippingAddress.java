package com.app.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class ShippingAddress {

    @Column(name = "shipping_recipient", length = 100)
    private String recipient;
    @Column(name = "shipping_phone", length = 20)
    private String phone;
    @Column(name = "shipping_street", length = 255)
    private String street;
    @Column(name = "shipping_ward", length = 100)
    private String ward;
    @Column(name = "shipping_district", length = 100)
    private String district;
    @Column(name = "shipping_province", length = 100)
    private String province;
    @Column(name = "shipping_postal_code", length = 20)
    private String postalCode;

    protected ShippingAddress() {
    }

    public ShippingAddress(
            String recipient,
            String phone,
            String street,
            String ward,
            String district,
            String province,
            String postalCode
    ) {
        this.recipient = recipient;
        this.phone = phone;
        this.street = street;
        this.ward = ward;
        this.district = district;
        this.province = province;
        this.postalCode = postalCode;
    }

    public String getRecipient() {
        return recipient;
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

    @Override
    public boolean equals(
            Object other
    ) {
        if (!(other instanceof ShippingAddress address)) {
            return false;
        }
        return Objects.equals(recipient, address.recipient) && Objects.equals(phone, address.phone)
                && Objects.equals(street, address.street) && Objects.equals(ward, address.ward)
                && Objects.equals(district, address.district) && Objects.equals(province, address.province)
                && Objects.equals(postalCode, address.postalCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipient, phone, street, ward, district, province, postalCode);
    }
}
