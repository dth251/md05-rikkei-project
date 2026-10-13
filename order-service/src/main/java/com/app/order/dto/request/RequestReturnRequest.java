package com.app.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestReturnRequest(
        @NotBlank
        @Size(max = 128)
        String idempotencyKey,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
