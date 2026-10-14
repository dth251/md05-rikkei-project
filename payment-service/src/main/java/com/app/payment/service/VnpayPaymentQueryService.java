package com.app.payment.service;

import com.app.payment.dto.request.VnpayCallback;

public interface VnpayPaymentQueryService {

    void reconcile(VnpayCallback callback);
}
