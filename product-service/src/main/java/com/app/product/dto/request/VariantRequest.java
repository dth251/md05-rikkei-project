package com.app.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record VariantRequest(
        @NotNull
        @Valid
        ProductRequest product,
        @NotNull
        @Size(min = 1, max = 10)
        Map<@Pattern(regexp = "[a-z][a-z0-9_-]{0,31}") String,
                @NotBlank @Size(max = 64) String> attributes
) {
}
