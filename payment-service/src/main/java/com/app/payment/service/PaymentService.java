package com.app.payment.service;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.entity.PaymentStatus;

public interface PaymentService {

    void acceptOrder(CommerceEvent event);

    PaymentResponse get(Long id, Long userId, boolean administrator);

    PaymentResponse getByOrder(Long orderId, Long userId, boolean administrator);

    PaymentResponse simulate(Long id, PaymentStatus status);
}
