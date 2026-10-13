package com.app.payment.service.impl;

import com.app.payment.config.VnpaySettings;
import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.request.VnpayLedgerRequest;
import com.app.payment.dto.response.VnpayLedgerResponse;
import com.app.payment.dto.response.VnpayRefundResponse;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import com.app.payment.entity.VnpayPayment;
import com.app.payment.entity.VnpayRefund;
import com.app.payment.entity.VnpayRefundState;
import com.app.payment.mapper.VnpayMapper;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.repository.VnpayPaymentRepository;
import com.app.payment.repository.VnpayRefundRepository;
import com.app.payment.service.VnpayRefundTransactionService;
import com.app.payment.service.ReturnRefundService;
import java.math.BigInteger;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VnpayRefundTransactionServiceImpl implements VnpayRefundTransactionService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final PaymentRepository payments;
    private final VnpayPaymentRepository captures;
    private final VnpayRefundRepository refunds;
    private final VnpaySettings settings;
    private final VnpayMapper mapper;
    private final String serverIp;
    private final ReturnRefundService returnRefunds;

    public VnpayRefundTransactionServiceImpl(
            PaymentRepository payments,
            VnpayPaymentRepository captures,
            VnpayRefundRepository refunds,
            VnpaySettings settings,
            VnpayMapper mapper,
            @Value("${payment.vnpay.server-ip:}") String serverIp,
            ReturnRefundService returnRefunds
    ) {
        this.payments = payments;
        this.captures = captures;
        this.refunds = refunds;
        this.settings = settings;
        this.mapper = mapper;
        this.serverIp = serverIp;
        this.returnRefunds = returnRefunds;
    }

    @Override
    @Transactional
    public VnpayLedgerRequest prepareRefund(Long paymentId, Long administratorId) {
        var payment = payments.findLockedById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        var existing = refunds.findById(paymentId);
        if (existing.isPresent()) {
            return new VnpayLedgerRequest(paymentId, existing.get().getRequestId(), false, Map.of());
        }
        if (administratorId == null || administratorId <= 0 || payment.getStatus() != PaymentStatus.SUCCESS
                || payment.getRefundStatus() != RefundStatus.REQUIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "Captured payment requiring refund expected");
        }
        var capture = captured(paymentId);
        var refund = refunds.saveAndFlush(new VnpayRefund(paymentId, identifier(), administratorId, Instant.now(), numericServerIp()));
        var parameters = common(capture, refund.getRequestId(), "refund", refund.getRequestedAt(), refund.getServerIp());
        parameters.put("vnp_TransactionType", "02");
        parameters.put("vnp_Amount", capture.getAmountMinor());
        parameters.put("vnp_TransactionNo", capture.getTransactionNumber());
        parameters.put("vnp_CreateBy", "ADMIN" + administratorId);
        return new VnpayLedgerRequest(paymentId, refund.getRequestId(), true, parameters);
    }

    @Override
    @Transactional
    public VnpayLedgerRequest prepareQuery(Long paymentId) {
        payments.findLockedById(paymentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        var refund = refunds.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "No refund request to reconcile"));
        if (refund.getState() == VnpayRefundState.COMPLETED) {
            return new VnpayLedgerRequest(paymentId, refund.getRequestId(), false, Map.of());
        }
        var parameters = common(captured(paymentId), identifier(), "querydr", Instant.now(), numericServerIp());
        if (refund.getTransactionNumber() != null) {
            parameters.put("vnp_TransactionNo", refund.getTransactionNumber());
        }
        return new VnpayLedgerRequest(paymentId, refund.getRequestId(), true, parameters);
    }

    @Override
    @Transactional
    public VnpayRefundResponse record(VnpayLedgerRequest request, VnpayLedgerResponse response) {
        var payment = payments.findLockedById(request.paymentId()).orElseThrow();
        var refund = refunds.findById(request.paymentId()).orElseThrow();
        if (!refund.getRequestId().equals(request.refundRequestId())) {
            throw new IllegalStateException("Refund request changed");
        }
        if (!request.send() || response == null || refund.getState() == VnpayRefundState.COMPLETED) {
            return mapper.toRefund(refund);
        }
        var parameters = response.parameters();
        var capture = captures.findById(request.paymentId()).orElseThrow();
        String code = parameters.getOrDefault("vnp_ResponseCode", "");
        String type = parameters.getOrDefault("vnp_TransactionType", "");
        String status = parameters.getOrDefault("vnp_TransactionStatus", "");
        String transaction = parameters.getOrDefault("vnp_TransactionNo", "");
        boolean matching = code.matches("[0-9]{2}")
                && request.parameters().get("vnp_Command").equals(parameters.get("vnp_Command"))
                && capture.getTerminal().equals(parameters.get("vnp_TmnCode"))
                && capture.getReference().equals(parameters.get("vnp_TxnRef"))
                && sameAmount(capture.getAmountMinor(), parameters.get("vnp_Amount"));
        if (!matching) {
            return mapper.toRefund(refund);
        }
        // A successful query of the original PAYMENT (type 01) cannot prove a refund completed.
        if (!"02".equals(type)) {
            return mapper.toRefund(refund);
        }
        if (transaction.matches("[0-9]{1,15}") && new BigInteger(transaction).signum() > 0) {
            transaction = new BigInteger(transaction).toString();
        } else {
            transaction = null;
        }
        if (refund.getTransactionNumber() != null && transaction != null
                && !refund.getTransactionNumber().equals(transaction)) {
            return mapper.toRefund(refund);
        }
        if ("00".equals(code) && "00".equals(status) && transaction != null) {
            payment.recordRefund();
            returnRefunds.refundCompleted(payment);
            refund.record(VnpayRefundState.COMPLETED, transaction, code);
        } else if ("05".equals(status) || "06".equals(status) || "94".equals(code)) {
            refund.record(VnpayRefundState.PROCESSING, transaction, code);
        } else if ("00".equals(code) && ("02".equals(status) || "09".equals(status))) {
            refund.record(VnpayRefundState.REJECTED, transaction, code);
        } else {
            // Other codes remain uncertain and never authorize another refund submission.
            refund.record(refund.getState(), transaction, code);
        }
        refunds.flush();
        payments.flush();
        return mapper.toRefund(refund);
    }

    private VnpayPayment captured(Long paymentId) {
        var capture = captures.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Verified VNPay capture required"));
        if (capture.getTransactionNumber() == null || !settings.terminal().equals(capture.getTerminal())) {
            throw new ApiException(HttpStatus.CONFLICT, "Verified VNPay capture under current merchant required");
        }
        return capture;
    }

    private LinkedHashMap<String, String> common(VnpayPayment capture, String requestId, String command,
                                                Instant createdAt, String ip) {
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("vnp_RequestId", requestId);
        parameters.put("vnp_Version", "2.1.0");
        parameters.put("vnp_Command", command);
        parameters.put("vnp_TmnCode", capture.getTerminal());
        parameters.put("vnp_TxnRef", capture.getReference());
        parameters.put("vnp_TransactionDate", TIME.format(capture.getCreatedAt()));
        parameters.put("vnp_CreateDate", TIME.format(createdAt));
        parameters.put("vnp_IpAddr", ip);
        parameters.put("vnp_OrderInfo", "Hoan tien " + capture.getReference());
        return parameters;
    }

    private boolean sameAmount(String expected, String actual) {
        return actual != null && actual.matches("[0-9]{1,12}") && expected.equals(new BigInteger(actual).toString());
    }

    private String identifier() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String numericServerIp() {
        if (serverIp == null || serverIp.length() > 45 || !(serverIp.matches("[0-9.]{7,15}")
                || (serverIp.contains(":") && serverIp.matches("[0-9a-fA-F:]{3,45}")))) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPay merchant server address required");
        }
        try {
            return InetAddress.getByName(serverIp).getHostAddress();
        } catch (java.net.UnknownHostException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPay merchant server address required");
        }
    }
}
