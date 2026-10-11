package com.app.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 100)
        String recipientName,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{9,15}")
        String phone,
        @NotBlank @Size(max = 255)
        String street,
        @NotBlank @Size(max = 100)
        String ward,
        @NotBlank @Size(max = 100)
        String district,
        @NotBlank @Size(max = 100)
        String province,
        @Size(max = 10)
        String postalCode,
        boolean defaultAddress
) {
}
