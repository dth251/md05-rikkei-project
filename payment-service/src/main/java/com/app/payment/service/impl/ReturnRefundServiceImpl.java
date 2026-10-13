package com.app.payment.service.impl;

import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.request.CashRefundRequest;
import com.app.payment.dto.response.CashRefundResponse;
import com.app.payment.entity.CashRefund;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentMethod;
import com.app.payment.entity.RefundStatus;
import com.app.payment.entity.ReturnedOrder;
import com.app.payment.mapper.CashRefundMapper;
import com.app.payment.mapper.PaymentMapper;
import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.repository.VnpayPaymentRepository;
import com.app.payment.repository.CashRefundRepository;
import com.app.payment.repository.EventInbox;
import com.app.payment.repository.PaymentOrderLock;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.repository.ReturnedOrderRepository;
import com.app.payment.service.OutboxService;
import com.app.payment.service.ReturnRefundService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReturnRefundServiceImpl implements ReturnRefundService {

    private final PaymentRepository payments;
    private final ReturnedOrderRepository returnedOrders;
    private final CashRefundRepository cashRefunds;
    private final PaymentOrderLock orderLocks;
    private final EventInbox inbox;
    private final OutboxService outbox;
    private final CashRefundMapper mapper;
    private final PaymentMapper paymentMapper;
    private final VnpayPaymentRepository providerPayments;

    public ReturnRefundServiceImpl(
            PaymentRepository payments,
            ReturnedOrderRepository returnedOrders,
            CashRefundRepository cashRefunds,
            PaymentOrderLock orderLocks,
            EventInbox inbox,
            OutboxService outbox,
            CashRefundMapper mapper,
            PaymentMapper paymentMapper,
            VnpayPaymentRepository providerPayments
    ) {
        this.payments = payments;
        this.returnedOrders = returnedOrders;
        this.cashRefunds = cashRefunds;
        this.orderLocks = orderLocks;
        this.inbox = inbox;
        this.outbox = outbox;
        this.mapper = mapper;
        this.paymentMapper = paymentMapper;
        this.providerPayments = providerPayments;
    }

    @Override
    @Transactional
    public void accept(CommerceEvent event) {
        if (!"order.return-received".equals(event.type())) {
            throw new IllegalArgumentException("Invalid return receipt event");
        }
        orderLocks.lock(event.orderId());
        if (!inbox.claim(event.eventId(), event.type())) {
            return;
        }
        Payment payment = payments.findLockedByOrderId(event.orderId()).orElseThrow(() ->
                new IllegalArgumentException("Return receipt requires an existing captured payment"));
        if (!payment.getUserId().equals(event.userId()) || payment.getAmount().compareTo(event.amount()) != 0
                || (event.paymentId() != null && !payment.getId().equals(event.paymentId()))) {
            throw new IllegalArgumentException("Return event identity or amount mismatch");
        }
        var marker = returnedOrders.findById(event.orderId()).orElseGet(() ->
                returnedOrders.saveAndFlush(new ReturnedOrder(event.orderId(), event.userId(), event.amount(),
                        payment.getId(), event.occurredAt())));
        validate(marker, payment);
        apply(payment);
    }

    @Override
    @Transactional
    public void apply(Payment payment) {
        var marker = returnedOrders.findById(payment.getOrderId());
        if (marker.isEmpty()) {
            return;
        }
        validate(marker.get(), payment);
        payment.requireReturnRefund();
        if (marker.get().markReady()) {
            append(payment, "payment.return-refund-ready");
        }
        if (payment.getRefundStatus() == RefundStatus.REFUNDED) {
            refundCompleted(payment);
        }
        payments.flush();
    }

    @Override
    @Transactional
    public CashRefundResponse refundCash(
            Long paymentId,
            Long administratorId,
            CashRefundRequest request
    ) {
        Payment payment = payments.findLockedById(paymentId).orElseThrow(this::notFound);
        if (payment.getPaymentMethod() != PaymentMethod.COD || payment.getCodCollectedAt() == null) {
            throw conflict("Only collected COD payments can refund cash");
        }
        if (request.amount().compareTo(payment.getAmount()) != 0) {
            throw conflict("Refund the full order amount");
        }
        String key = request.idempotencyKey().strip();
        String receipt = request.receiptNumber().strip();
        var existing = cashRefunds.findById(paymentId);
        if (existing.isPresent()) {
            if (!existing.get().matches(key, receipt, administratorId)) {
                throw conflict("Cash refund already recorded with different details");
            }
            return mapper.toResponse(existing.get());
        }
        var marker = returnedOrders.findById(payment.getOrderId()).orElseThrow(() ->
                conflict("Returned goods have not been acknowledged yet"));
        validate(marker, payment);
        if (payment.getRefundStatus() != RefundStatus.REQUIRED) {
            throw conflict("Payment is not awaiting a return refund");
        }
        var record = cashRefunds.saveAndFlush(new CashRefund(paymentId, payment.getAmount(), key, receipt, administratorId));
        payment.recordRefund();
        refundCompleted(payment);
        payments.flush();
        return mapper.toResponse(record);
    }

    @Override
    @Transactional(readOnly = true)
    public CashRefundResponse getCashRefund(
            Long paymentId,
            Long userId,
            boolean administrator
    ) {
        Payment payment = payments.findById(paymentId).orElseThrow(this::notFound);
        if (!administrator && !payment.getUserId().equals(userId)) {
            throw notFound();
        }
        return mapper.toResponse(cashRefunds.findById(paymentId).orElseThrow(this::notFound));
    }

    @Override
    @Transactional
    public void refundCompleted(Payment payment) {
        var marker = returnedOrders.findById(payment.getOrderId());
        if (marker.isEmpty()) {
            return; // Existing refunds for expired online orders retain their original flow.
        }
        validate(marker.get(), payment);
        if (payment.getRefundStatus() != RefundStatus.REFUNDED) {
            throw new IllegalStateException("Verified completed refund expected");
        }
        if (marker.get().markRefunded()) {
            append(payment, "payment.return-refunded");
        }
    }

    @Override
    @Transactional
    public PaymentResponse simulateRefund(Long paymentId) {
        Payment payment = payments.findLockedById(paymentId).orElseThrow(this::notFound);
        if (payment.getPaymentMethod() != PaymentMethod.ONLINE || providerPayments.existsById(paymentId)) {
            throw conflict("Only simulated online payments can simulate a refund");
        }
        var marker = returnedOrders.findById(payment.getOrderId()).orElseThrow(() ->
                conflict("Returned goods have not been acknowledged yet"));
        validate(marker, payment);
        if (payment.getRefundStatus() == RefundStatus.REFUNDED) {
            return paymentMapper.toResponse(payment);
        }
        if (payment.getRefundStatus() != RefundStatus.REQUIRED) {
            throw conflict("Payment is not awaiting a return refund");
        }
        payment.recordRefund();
        refundCompleted(payment);
        payments.flush();
        return paymentMapper.toResponse(payment);
    }

    private void validate(
            ReturnedOrder marker,
            Payment payment
    ) {
        if (!marker.matches(payment.getUserId(), payment.getAmount(), payment.getId())) {
            throw new IllegalArgumentException("Return receipt does not match payment");
        }
    }

    private void append(
            Payment payment,
            String type
    ) {
        outbox.append(new CommerceEvent(UUID.randomUUID().toString(), type, payment.getOrderId(), payment.getUserId(),
                payment.getAmount(), payment.getId(), Instant.now()));
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Cash refund not found");
    }
}
