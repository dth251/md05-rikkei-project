package com.app.order.dto.response;

public record ShippingAddressResponse(
        String recipientName,
        String phone,
        String street,
        String ward,
        String district,
        String province,
        String postalCode
) {
}
