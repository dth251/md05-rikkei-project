package com.app.payment.service.impl;

import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.request.CollectCodRequest;
import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.entity.CodDelivery;
import com.app.payment.entity.CodDeliveryState;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentMethod;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.mapper.PaymentMapper;
import com.app.payment.repository.CodDeliveryRepository;
import com.app.payment.repository.EventInbox;
import com.app.payment.repository.PaymentOrderLock;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.service.CodPaymentService;
import com.app.payment.service.OutboxService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodPaymentServiceImpl implements CodPaymentService {

    private final PaymentRepository payments;
    private final CodDeliveryRepository deliveries;
    private final PaymentOrderLock orderLocks;
    private final EventInbox inbox;
    private final OutboxService outbox;
    private final PaymentMapper mapper;

    public CodPaymentServiceImpl(
            PaymentRepository payments,
            CodDeliveryRepository deliveries,
            PaymentOrderLock orderLocks,
            EventInbox inbox,
            OutboxService outbox,
            PaymentMapper mapper
    ) {
        this.payments = payments;
        this.deliveries = deliveries;
        this.orderLocks = orderLocks;
        this.inbox = inbox;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void accept(CommerceEvent event) {
        orderLocks.lock(event.orderId());
        if (!inbox.claim(event.eventId(), event.type())) {
            return;
        }
        CodDeliveryState state = switch (event.type()) {
            case "order.cod-shipped" -> CodDeliveryState.SHIPPED;
            case "order.cod-cancelled" -> CodDeliveryState.CANCELLED;
            default -> throw new IllegalArgumentException("Invalid COD lifecycle event");
        };
        var existing = deliveries.findById(event.orderId());
        if (existing.isPresent()) {
            validate(existing.get(), event.userId(), event.amount());
            if (existing.get().getState() != state) {
                throw new IllegalArgumentException("Conflicting COD delivery state");
            }
        } else {
            deliveries.saveAndFlush(new CodDelivery(event.orderId(), event.userId(), event.amount(), state,
                    event.occurredAt()));
        }
        // A durable marker also handles lifecycle events arriving before the creation event.
        payments.findLockedByOrderId(event.orderId()).ifPresent(this::apply);
    }

    @Override
    @Transactional
    public void apply(Payment payment) {
        var delivery = deliveries.findById(payment.getOrderId());
        if (delivery.isEmpty()) {
            return;
        }
        if (payment.getPaymentMethod() != PaymentMethod.COD) {
            throw new IllegalArgumentException("COD lifecycle cannot affect online payment");
        }
        validate(delivery.get(), payment.getUserId(), payment.getAmount());
        if (delivery.get().getState() == CodDeliveryState.CANCELLED) {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                throw new IllegalArgumentException("Collected COD cannot cancel");
            }
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.complete(PaymentStatus.FAILED);
                append(payment, "payment.failed");
                payments.flush();
            }
        }
    }

    @Override
    @Transactional
    public PaymentResponse collect(
            Long paymentId,
            CollectCodRequest request,
            Long collectorId
    ) {
        Payment payment = payments.findLockedById(paymentId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getPaymentMethod() != PaymentMethod.COD) {
            throw new ApiException(HttpStatus.CONFLICT, "Only COD payments can record cash collection");
        }
        if (request.amount() == null || request.amount().compareTo(payment.getAmount()) != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Collect the full order amount");
        }
        String key = request.idempotencyKey().strip();
        String receipt = request.receiptNumber().strip();
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            if (!key.equals(payment.getCodIdempotencyKey()) || !receipt.equals(payment.getCodReceiptNumber())
                    || !collectorId.equals(payment.getCodCollectedBy())) {
                throw new ApiException(HttpStatus.CONFLICT, "COD collection already recorded with different details");
            }
            return mapper.toResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "COD payment is no longer pending");
        }
        CodDelivery delivery = deliveries.findById(payment.getOrderId()).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "COD shipment has not been acknowledged yet"));
        validate(delivery, payment.getUserId(), payment.getAmount());
        if (delivery.getState() != CodDeliveryState.SHIPPED) {
            throw new ApiException(HttpStatus.CONFLICT, "Cancelled COD cannot collect cash");
        }
        payment.collectCod(key, receipt, collectorId);
        append(payment, "payment.succeeded");
        payments.flush();
        return mapper.toResponse(payment);
    }

    private void validate(CodDelivery delivery, Long userId, java.math.BigDecimal amount) {
        if (!delivery.getUserId().equals(userId) || delivery.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("COD event identity or amount mismatch");
        }
    }

    private void append(Payment payment, String type) {
        outbox.append(new CommerceEvent(UUID.randomUUID().toString(), type, payment.getOrderId(), payment.getUserId(),
                payment.getAmount(), payment.getId(), Instant.now()));
    }
}
