package com.app.payment.service;

import com.app.payment.dto.request.VnpayLedgerRequest;
import com.app.payment.dto.response.VnpayLedgerResponse;
import com.app.payment.dto.response.VnpayRefundResponse;

public interface VnpayRefundTransactionService {

    VnpayLedgerRequest prepareRefund(Long paymentId, Long administratorId);

    VnpayLedgerRequest prepareQuery(Long paymentId);

    VnpayRefundResponse record(VnpayLedgerRequest request, VnpayLedgerResponse response);
}
