package com.app.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReceiveReturnRequest(
        @NotBlank
        @Size(max = 128)
        String warehouseReceipt,

        @NotNull
        Boolean restock
) {
}
