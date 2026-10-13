package com.app.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CashRefundRequest(
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 16, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Size(max = 128)
        String idempotencyKey,

        @NotBlank
        @Size(max = 128)
        String receiptNumber
) {
}
