package com.app.payment.service.impl;

import com.app.payment.config.VnpaySettings;
import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.request.VnpayLedgerRequest;
import com.app.payment.dto.response.VnpayLedgerResponse;
import com.app.payment.dto.response.VnpayRefundResponse;
import com.app.payment.service.VnpayLedgerClient;
import com.app.payment.service.VnpayRefundService;
import com.app.payment.service.VnpayRefundTransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class VnpayRefundServiceImpl implements VnpayRefundService {

    private final VnpaySettings settings;
    private final VnpayRefundTransactionService transactions;
    private final VnpayLedgerClient ledger;

    public VnpayRefundServiceImpl(
            VnpaySettings settings,
            VnpayRefundTransactionService transactions,
            VnpayLedgerClient ledger
    ) {
        this.settings = settings;
        this.transactions = transactions;
        this.ledger = ledger;
    }

    @Override
    public VnpayRefundResponse submit(Long paymentId, Long administratorId) {
        requireEnabled();
        // The committed claim precedes network IO. A retry never sends the refund POST again.
        return execute(transactions.prepareRefund(paymentId, administratorId));
    }

    @Override
    public VnpayRefundResponse reconcile(Long paymentId) {
        requireEnabled();
        return execute(transactions.prepareQuery(paymentId));
    }

    private VnpayRefundResponse execute(VnpayLedgerRequest request) {
        VnpayLedgerResponse response = null;
        if (request.send()) {
            try {
                response = ledger.execute(request);
            } catch (IllegalStateException exception) {
                // A timeout or invalid reply cannot establish whether funds moved. Only querydr follows.
            }
        }
        return transactions.record(request, response);
    }

    private void requireEnabled() {
        if (!settings.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPay sandbox unavailable");
        }
    }
}
