package com.app.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductSearchRequest(
        @Min(0)
        @Max(10000)
        Integer page,
        @Min(1)
        @Max(100)
        Integer size,
        @Size(max = 100)
        String q,
        @Size(max = 100)
        String category,
        @Size(max = 100)
        String brand,
        @DecimalMin("0.01")
        @DecimalMax("999999999999.99")
        @Digits(integer = 12, fraction = 2)
        BigDecimal minPrice,
        @DecimalMin("0.01")
        @DecimalMax("999999999999.99")
        @Digits(integer = 12, fraction = 2)
        BigDecimal maxPrice,
        @Pattern(regexp = "id|name|price-asc|price-desc|newest")
        String sort
) {
}
