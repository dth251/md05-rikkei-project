package com.app.payment.service;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.entity.Payment;

public interface PaymentExpiryService {

    void accept(CommerceEvent event);

    void apply(Payment payment);
}
