package com.app.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecideReturnRequest(
        @NotNull
        Boolean approved,

        @NotBlank
        @Size(max = 500)
        String note
) {
}
