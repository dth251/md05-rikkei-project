package com.app.payment.service.impl;

import com.app.payment.config.VnpaySettings;
import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.request.VnpayCallback;
import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.dto.response.VnpayIpnResponse;
import com.app.payment.dto.response.VnpayReturnResponse;
import com.app.payment.security.VnpaySignature;
import com.app.payment.service.VnpayService;
import com.app.payment.service.VnpayTransactionService;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

@Service
public class VnpayServiceImpl implements VnpayService {

    private static final DateTimeFormatter PROVIDER_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private final VnpaySettings settings;
    private final VnpayTransactionService transactions;

    public VnpayServiceImpl(VnpaySettings settings, VnpayTransactionService transactions) {
        this.settings = settings;
        this.transactions = transactions;
    }

    @Override
    public VnpayCheckoutResponse checkout(Long paymentId, Long userId, String clientIp) {
        requireEnabled();
        return transactions.checkout(paymentId, userId, clientIp);
    }

    @Override
    public VnpayIpnResponse ipn(MultiValueMap<String, String> parameters) {
        if (!settings.enabled()) {
            return new VnpayIpnResponse("99", "Provider unavailable");
        }
        VnpayCallback callback;
        try {
            callback = verified(parameters);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            return new VnpayIpnResponse("97", "Invalid signature or parameters");
        }
        try {
            // Separate transactional bean: a commit/constraint failure rolls back before this response is sent.
            return transactions.apply(callback);
        } catch (RuntimeException exception) {
            return new VnpayIpnResponse("99", "Confirmation unavailable");
        }
    }

    @Override
    public VnpayReturnResponse verifyReturn(MultiValueMap<String, String> parameters) {
        requireEnabled();
        try {
            var callback = verified(parameters);
            return new VnpayReturnResponse(callback.reference(), callback.successful() ? "SUCCESS" : "FAILED",
                    "Provider return validated; IPN determines persisted payment state");
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid provider return");
        }
    }

    private VnpayCallback verified(MultiValueMap<String, String> parameters) {
        if (parameters == null || parameters.isEmpty() || parameters.size() > 32) {
            throw new IllegalArgumentException("Provider parameters required");
        }
        Map<String, String> flattened = new TreeMap<>();
        int total = 0;
        for (var entry : parameters.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().size() != 1
                    || entry.getValue().getFirst() == null) {
                throw new IllegalArgumentException("Duplicate provider parameters");
            }
            String value = entry.getValue().getFirst();
            total += entry.getKey().length() + value.length();
            if (total > 8192) {
                throw new IllegalArgumentException("Provider parameters exceed limit");
            }
            flattened.put(entry.getKey(), value);
        }
        if (!VnpaySignature.verifyQuery(flattened, settings.secret())
                || !settings.terminal().equals(flattened.get("vnp_TmnCode"))
                || !"VND".equals(flattened.getOrDefault("vnp_CurrCode", "VND"))) {
            throw new IllegalArgumentException("Invalid provider signature");
        }
        String reference = required(flattened, "vnp_TxnRef", "[0-9a-f]{32}");
        String amount = required(flattened, "vnp_Amount", "[0-9]{1,12}");
        String response = required(flattened, "vnp_ResponseCode", "[0-9]{2}");
        String status = required(flattened, "vnp_TransactionStatus", "[0-9]{2}");
        String number = required(flattened, "vnp_TransactionNo", "[0-9]{1,15}");
        number = new BigInteger(number).toString();
        boolean successful = "00".equals(response) && "00".equals(status);
        Instant paidAt = null;
        if (successful) {
            if (new BigInteger(number).signum() <= 0) {
                throw new IllegalArgumentException("Capture reference required");
            }
            paidAt = LocalDateTime.parse(required(flattened, "vnp_PayDate", "[0-9]{14}"), PROVIDER_TIME)
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            if (paidAt.isAfter(Instant.now().plusSeconds(300))) {
                throw new IllegalArgumentException("Capture date invalid");
            }
        }
        return new VnpayCallback(reference, new BigInteger(amount).toString(), successful, number, paidAt);
    }

    private String required(Map<String, String> parameters, String name, String pattern) {
        String value = parameters.get(name);
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException("Provider parameter invalid");
        }
        return value;
    }

    private void requireEnabled() {
        if (!settings.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPay sandbox is not configured");
        }
    }
}
