package com.app.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        String sku,

        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 2000)
        String description,

        @NotNull
        @DecimalMin("0.01")
        @DecimalMax("999999999999.99")
        @Digits(integer = 12, fraction = 2)
        BigDecimal unitPrice,

        @Size(max = 100)
        String category,

        @Size(max = 100)
        String brand
) {
}
