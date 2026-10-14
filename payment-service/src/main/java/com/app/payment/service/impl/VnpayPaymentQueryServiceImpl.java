package com.app.payment.service.impl;

import com.app.payment.config.VnpaySettings;
import com.app.payment.dto.request.VnpayCallback;
import com.app.payment.security.VnpayLedgerSignature;
import com.app.payment.service.VnpayLedgerClient;
import com.app.payment.service.VnpayPaymentQueryService;
import com.app.payment.service.VnpayTransactionService;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class VnpayPaymentQueryServiceImpl implements VnpayPaymentQueryService {

    private static final DateTimeFormatter PROVIDER_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private final VnpayTransactionService transactions;
    private final VnpayLedgerClient ledger;
    private final VnpaySettings settings;
    private final Map<String, Instant> nextQueries = new HashMap<>();

    public VnpayPaymentQueryServiceImpl(
            VnpayTransactionService transactions,
            VnpayLedgerClient ledger,
            VnpaySettings settings
    ) {
        this.transactions = transactions;
        this.ledger = ledger;
        this.settings = settings;
    }

    @Override
    public void reconcile(VnpayCallback callback) {
        if (!callback.successful() || !claim(callback.reference())) {
            return;
        }
        try {
            var request = transactions.preparePaymentQuery(callback);
            if (!request.send()) {
                return;
            }
            // Network IO is outside the database transaction and its payment lock.
            var response = ledger.execute(request);
            var parameters = response.parameters();
            if (!VnpayLedgerSignature.verifyResponse(parameters, "querydr", settings.secret())
                    || !"querydr".equals(parameters.getOrDefault("vnp_Command", "querydr"))
                    || !settings.terminal().equals(parameters.get("vnp_TmnCode"))
                    || !callback.reference().equals(parameters.get("vnp_TxnRef"))
                    || !"00".equals(parameters.get("vnp_ResponseCode"))
                    || !"01".equals(parameters.get("vnp_TransactionType"))
                    || !"00".equals(parameters.get("vnp_TransactionStatus"))
                    || !sameNumber(callback.amountMinor(), parameters.get("vnp_Amount"), 12)
                    || !sameNumber(callback.transactionNumber(), parameters.get("vnp_TransactionNo"), 15)) {
                return;
            }
            String payDate = parameters.getOrDefault("vnp_PayDate", "");
            if (!payDate.matches("[0-9]{14}")) {
                return;
            }
            Instant paidAt = LocalDateTime.parse(payDate, PROVIDER_TIME)
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            if (!paidAt.equals(callback.paidAt())) {
                return;
            }
            // Same capture transaction as IPN: one event, and late captures require refund.
            transactions.apply(new VnpayCallback(callback.reference(), callback.amountMinor(),
                    true, callback.transactionNumber(), paidAt));
        } catch (RuntimeException exception) {
            // Timeout, invalid provider reply or failed commit leaves the stored state intact.
            // Never log credentials or signed provider payloads.
        }
    }

    private synchronized boolean claim(String reference) {
        Instant now = Instant.now();
        nextQueries.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        if (nextQueries.containsKey(reference) || nextQueries.size() >= 256) {
            return false;
        }
        nextQueries.put(reference, now.plusSeconds(60));
        return true;
    }

    private boolean sameNumber(String expected, String actual, int maxLength) {
        return actual != null && actual.matches("[0-9]{1," + maxLength + "}")
                && expected.equals(new BigInteger(actual).toString());
    }
}
