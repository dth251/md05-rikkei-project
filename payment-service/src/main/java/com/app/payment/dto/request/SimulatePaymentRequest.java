package com.app.payment.dto.request;

import com.app.payment.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record SimulatePaymentRequest(
        @NotNull
        PaymentStatus status
) {
}
