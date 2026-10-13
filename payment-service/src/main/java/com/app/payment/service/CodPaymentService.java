package com.app.payment.service;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.request.CollectCodRequest;
import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.entity.Payment;

public interface CodPaymentService {
    void accept(CommerceEvent event);

    void apply(Payment payment);

    PaymentResponse collect(Long paymentId, CollectCodRequest request, Long collectorId);
}
