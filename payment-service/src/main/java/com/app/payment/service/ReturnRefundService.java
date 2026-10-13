package com.app.payment.service;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.request.CashRefundRequest;
import com.app.payment.dto.response.CashRefundResponse;
import com.app.payment.entity.Payment;
import com.app.payment.dto.response.PaymentResponse;

public interface ReturnRefundService {
    void accept(CommerceEvent event);

    void apply(Payment payment);

    CashRefundResponse refundCash(
            Long paymentId,
            Long administratorId,
            CashRefundRequest request
    );

    CashRefundResponse getCashRefund(
            Long paymentId,
            Long userId,
            boolean administrator
    );

    void refundCompleted(Payment payment);

    PaymentResponse simulateRefund(Long paymentId);
}
