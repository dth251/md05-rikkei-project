package com.app.payment.mapper;

import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.entity.VnpayPayment;
import org.springframework.stereotype.Component;

@Component
public class VnpayMapper {

    public VnpayCheckoutResponse toCheckout(VnpayPayment payment) {
        return new VnpayCheckoutResponse(payment.getPaymentId(), payment.getReference(),
                payment.getCheckoutUrl(), payment.getExpiresAt());
    }
}
