package com.app.order.service;

import com.app.order.dto.event.CommerceEvent;

public interface OrderPaymentService {

    void accept(CommerceEvent event);
}
