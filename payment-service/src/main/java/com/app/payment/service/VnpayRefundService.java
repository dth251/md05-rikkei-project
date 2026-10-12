package com.app.payment.service;

import com.app.payment.dto.response.VnpayRefundResponse;

public interface VnpayRefundService {

    VnpayRefundResponse submit(Long paymentId, Long administratorId);

    VnpayRefundResponse reconcile(Long paymentId);
}
