package com.app.payment.service;

import com.app.payment.dto.request.VnpayLedgerRequest;
import com.app.payment.dto.response.VnpayLedgerResponse;

public interface VnpayLedgerClient {

    VnpayLedgerResponse execute(VnpayLedgerRequest request);
}
