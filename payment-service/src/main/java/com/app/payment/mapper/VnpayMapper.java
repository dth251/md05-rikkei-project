package com.app.payment.mapper;

import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.dto.request.VnpayCallback;
import com.app.payment.dto.response.VnpayReturnResponse;
import com.app.payment.entity.Payment;
import com.app.payment.entity.VnpayPayment;
import com.app.payment.entity.VnpayRefund;
import com.app.payment.dto.response.VnpayRefundResponse;
import org.springframework.stereotype.Component;

@Component
public class VnpayMapper {

    public VnpayReturnResponse toReturn(Payment payment, VnpayCallback callback) {
        return new VnpayReturnResponse(callback.reference(), callback.successful() ? "SUCCESS" : "FAILED",
                "IPN determines persisted payment state; order confirmation is asynchronous",
                payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getStatus(),
                payment.isReservationExpired(), payment.getRefundStatus());
    }

    public VnpayCheckoutResponse toCheckout(VnpayPayment payment) {
        return new VnpayCheckoutResponse(payment.getPaymentId(), payment.getReference(),
                payment.getCheckoutUrl(), payment.getExpiresAt());
    }

    public VnpayRefundResponse toRefund(VnpayRefund refund) {
        return new VnpayRefundResponse(refund.getPaymentId(), refund.getRequestId(), refund.getState(),
                refund.getResponseCode(), refund.getRequestedAt(), refund.getCheckedAt());
    }
}
