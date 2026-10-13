package com.app.payment.mapper;

import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.dto.response.CodReceiptResponse;
import com.app.payment.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmount(),
                payment.getStatus(), payment.getCreatedAt(), payment.getCompletedAt(),
                payment.isReservationExpired(), payment.getRefundStatus(), payment.getPaymentMethod(),
                payment.getCodCollectedAt() == null ? null : new CodReceiptResponse(payment.getCodReceiptNumber(),
                        payment.getCodCollectedBy(), payment.getCodCollectedAt()));
    }
}
