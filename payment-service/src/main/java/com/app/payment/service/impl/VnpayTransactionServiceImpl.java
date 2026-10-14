package com.app.payment.service.impl;

import com.app.payment.config.VnpaySettings;
import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.request.VnpayCallback;
import com.app.payment.dto.request.VnpayLedgerRequest;
import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.dto.response.VnpayIpnResponse;
import com.app.payment.dto.response.VnpayReturnResponse;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.PaymentMethod;
import com.app.payment.entity.VnpayPayment;
import com.app.payment.mapper.VnpayMapper;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.repository.VnpayPaymentRepository;
import com.app.payment.security.VnpaySignature;
import com.app.payment.service.OutboxService;
import com.app.payment.service.PaymentExpiryService;
import com.app.payment.service.VnpayTransactionService;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VnpayTransactionServiceImpl implements VnpayTransactionService {

    private static final DateTimeFormatter PROVIDER_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final PaymentRepository payments;
    private final VnpayPaymentRepository attempts;
    private final VnpaySettings settings;
    private final VnpayMapper mapper;
    private final PaymentExpiryService expiry;
    private final OutboxService outbox;
    private final String serverIp;

    public VnpayTransactionServiceImpl(
            PaymentRepository payments,
            VnpayPaymentRepository attempts,
            VnpaySettings settings,
            VnpayMapper mapper,
            PaymentExpiryService expiry,
            OutboxService outbox,
            @Value("${payment.vnpay.server-ip:}") String serverIp
    ) {
        this.payments = payments;
        this.attempts = attempts;
        this.settings = settings;
        this.mapper = mapper;
        this.expiry = expiry;
        this.outbox = outbox;
        this.serverIp = serverIp;
    }

    @Override
    @Transactional
    public VnpayCheckoutResponse checkout(Long paymentId, Long userId, String clientIp) {
        var payment = payments.findLockedById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (!payment.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            throw new ApiException(HttpStatus.CONFLICT, "COD cannot use VNPay checkout");
        }
        if (payment.getStatus() != PaymentStatus.PENDING || payment.isReservationExpired()) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment is no longer payable");
        }
        var existing = attempts.findById(paymentId);
        // VNPay signs seconds; matching database precision also keeps checkout replay DTOs identical.
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if (existing.isPresent()) {
            if (!existing.get().getExpiresAt().isAfter(now)) {
                throw new ApiException(HttpStatus.CONFLICT, "Checkout expired");
            }
            return mapper.toCheckout(existing.get());
        }
        String address = numericIp(clientIp);
        String amount = payment.getAmount().movePointRight(2).toBigIntegerExact().toString();
        if (!amount.matches("[1-9][0-9]{0,11}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount exceeds VNPay sandbox limit");
        }
        String reference = UUID.randomUUID().toString().replace("-", "");
        Instant expires = now.plusSeconds(900);
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_Version", "2.1.0");
        parameters.put("vnp_Command", "pay");
        parameters.put("vnp_TmnCode", settings.terminal());
        parameters.put("vnp_Amount", amount);
        parameters.put("vnp_CreateDate", PROVIDER_TIME.format(now));
        parameters.put("vnp_ExpireDate", PROVIDER_TIME.format(expires));
        parameters.put("vnp_CurrCode", "VND");
        parameters.put("vnp_IpAddr", address);
        parameters.put("vnp_Locale", "vn");
        parameters.put("vnp_OrderInfo", "Thanh toan " + reference);
        parameters.put("vnp_OrderType", "other");
        parameters.put("vnp_ReturnUrl", settings.returnUrl());
        parameters.put("vnp_TxnRef", reference);
        String query = VnpaySignature.canonicalQuery(parameters);
        String url = settings.paymentUrl() + "?" + query + "&vnp_SecureHash=" + VnpaySignature.hmac(query, settings.secret());
        var attempt = new VnpayPayment(paymentId, reference, amount, settings.terminal(), now, expires, address, url);
        return mapper.toCheckout(attempts.saveAndFlush(attempt));
    }

    @Override
    @Transactional(readOnly = true)
    public VnpayReturnResponse returnResult(VnpayCallback callback) {
        var attempt = attempts.findByReference(callback.reference())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Checkout not found"));
        if (!settings.terminal().equals(attempt.getTerminal())
                || !attempt.getAmountMinor().equals(callback.amountMinor())
                || (callback.successful() && attempt.getTransactionNumber() != null
                && !attempt.getTransactionNumber().equals(callback.transactionNumber()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Return does not match checkout");
        }
        var payment = payments.findById(attempt.getPaymentId()).orElseThrow();
        return mapper.toReturn(payment, callback);
    }

    @Override
    @Transactional(readOnly = true)
    public VnpayLedgerRequest preparePaymentQuery(VnpayCallback callback) {
        // Validate the signed return against stored checkout before any provider request.
        returnResult(callback);
        var attempt = attempts.findByReference(callback.reference()).orElseThrow();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        if (!callback.successful() || attempt.getTransactionNumber() != null) {
            return new VnpayLedgerRequest(attempt.getPaymentId(), requestId, false, Map.of());
        }
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_RequestId", requestId);
        parameters.put("vnp_Version", "2.1.0");
        parameters.put("vnp_Command", "querydr");
        parameters.put("vnp_TmnCode", attempt.getTerminal());
        parameters.put("vnp_TxnRef", attempt.getReference());
        parameters.put("vnp_TransactionNo", callback.transactionNumber());
        parameters.put("vnp_TransactionDate", PROVIDER_TIME.format(attempt.getCreatedAt()));
        parameters.put("vnp_CreateDate", PROVIDER_TIME.format(Instant.now()));
        parameters.put("vnp_IpAddr", numericIp(serverIp));
        parameters.put("vnp_OrderInfo", "Kiem tra thanh toan " + attempt.getReference());
        return new VnpayLedgerRequest(attempt.getPaymentId(), requestId, true, parameters);
    }

    @Override
    @Transactional
    public VnpayIpnResponse apply(VnpayCallback callback) {
        var paymentId = attempts.findPaymentIdByReference(callback.reference());
        if (paymentId.isEmpty()) {
            return new VnpayIpnResponse("01", "Payment not found");
        }
        // Lock Payment first, then load the attempt to avoid a stale entity after a concurrent IPN waits.
        var payment = payments.findLockedById(paymentId.get()).orElseThrow();
        var attempt = attempts.findById(paymentId.get()).orElseThrow();
        if (!settings.terminal().equals(attempt.getTerminal())) {
            return new VnpayIpnResponse("99", "Merchant configuration changed");
        }
        if (!attempt.getAmountMinor().equals(callback.amountMinor())) {
            return new VnpayIpnResponse("04", "Invalid amount");
        }
        if (attempt.getTransactionNumber() != null) {
            boolean conflictingCapture = callback.successful()
                    && !attempt.getTransactionNumber().equals(callback.transactionNumber());
            return new VnpayIpnResponse(conflictingCapture ? "99" : "02",
                    conflictingCapture ? "Conflicting capture" : "Payment already confirmed");
        }
        if (callback.successful()) {
            if (callback.paidAt().isBefore(attempt.getCreatedAt().minusSeconds(300))) {
                return new VnpayIpnResponse("99", "Invalid capture date");
            }
            expiry.apply(payment);
            boolean pendingBeforeCapture = payment.getStatus() == PaymentStatus.PENDING;
            payment.recordExternalCapture();
            attempt.capture(callback.transactionNumber(), callback.paidAt());
            if (pendingBeforeCapture) {
                outbox.append(new CommerceEvent(UUID.randomUUID().toString(), "payment.succeeded",
                        payment.getOrderId(), payment.getUserId(), payment.getAmount(), payment.getId(), Instant.now()));
            }
            // Previously failed/expired captures are retained with REQUIRED refund, without reviving the order.
        } else {
            if (attempt.isFailed()) {
                return new VnpayIpnResponse("02", "Payment already confirmed");
            }
            attempt.fail();
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.complete(PaymentStatus.FAILED);
                outbox.append(new CommerceEvent(UUID.randomUUID().toString(), "payment.failed",
                        payment.getOrderId(), payment.getUserId(), payment.getAmount(), payment.getId(), Instant.now()));
            }
        }
        attempts.flush();
        payments.flush();
        return new VnpayIpnResponse("00", "Confirm success");
    }

    private String numericIp(String address) {
        if (address == null || address.length() > 45 || !(address.matches("[0-9.]{7,15}")
                || (address.contains(":") && address.matches("[0-9a-fA-F:]{3,45}")))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Client address unavailable");
        }
        try {
            return InetAddress.getByName(address).getHostAddress();
        } catch (java.net.UnknownHostException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Client address unavailable");
        }
    }
}
