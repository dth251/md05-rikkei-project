package com.app.auth.dto.response;

public record AddressResponse(
        Long id,
        String recipientName,
        String phone,
        String street,
        String ward,
        String district,
        String province,
        String postalCode,
        boolean defaultAddress
) {
}
