package com.app.payment.service;

import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.dto.response.VnpayIpnResponse;
import com.app.payment.dto.response.VnpayReturnResponse;
import org.springframework.util.MultiValueMap;

public interface VnpayService {

    VnpayCheckoutResponse checkout(Long paymentId, Long userId, String clientIp);

    VnpayIpnResponse ipn(MultiValueMap<String, String> parameters);

    VnpayReturnResponse verifyReturn(MultiValueMap<String, String> parameters);
}
