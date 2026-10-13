package com.app.payment.service;

import com.app.payment.dto.request.VnpayCallback;
import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.dto.response.VnpayIpnResponse;

public interface VnpayTransactionService {

    VnpayCheckoutResponse checkout(Long paymentId, Long userId, String clientIp);

    VnpayIpnResponse apply(VnpayCallback callback);
}
