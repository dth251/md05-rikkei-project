package com.app.payment.service.impl;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.entity.ExpiredOrder;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.PaymentMethod;
import com.app.payment.repository.EventInbox;
import com.app.payment.repository.ExpiredOrderRepository;
import com.app.payment.repository.PaymentOrderLock;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.service.OutboxService;
import com.app.payment.service.PaymentExpiryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentExpiryServiceImpl implements PaymentExpiryService {

    private final ExpiredOrderRepository expiredOrders;
    private final PaymentRepository payments;
    private final PaymentOrderLock locks;
    private final EventInbox inbox;
    private final OutboxService outbox;

    public PaymentExpiryServiceImpl(
        ExpiredOrderRepository expiredOrders,
        PaymentRepository payments,
        PaymentOrderLock locks,
        EventInbox inbox,
        OutboxService outbox
    ) {
        this.expiredOrders = expiredOrders;
        this.payments = payments;
        this.locks = locks;
        this.inbox = inbox;
        this.outbox = outbox;
    }

    @Override
    @Transactional
    public void accept(CommerceEvent event) {
        locks.lock(event.orderId());
        if (!inbox.claim(event.eventId(), event.type())) {
            return;
        }
        ExpiredOrder expired = expiredOrders.findById(event.orderId()).orElseGet(() ->
                expiredOrders.saveAndFlush(new ExpiredOrder(event.orderId(), event.userId(), event.amount())));
        if (!expired.matches(event.userId(), event.amount())) {
            throw new IllegalArgumentException("Conflicting expiration event");
        }
        var payment = payments.findLockedByOrderId(event.orderId());
        if ("order.refund-requested".equals(event.type()) && (payment.isEmpty()
                || !payment.get().getId().equals(event.paymentId()) || payment.get().getStatus() != PaymentStatus.SUCCESS)) {
            throw new IllegalArgumentException("Refund request must match successful payment");
        }
        payment.ifPresent(this::apply);
    }

    @Override
    @Transactional
    public void apply(Payment payment) {
        var marker = expiredOrders.findById(payment.getOrderId());
        if (marker.isEmpty()) {
            return;
        }
        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            throw new IllegalArgumentException("COD cannot expire through online reservation policy");
        }
        if (!marker.get().matches(payment.getUserId(), payment.getAmount())) {
            throw new IllegalArgumentException("Expiration does not match payment");
        }
        boolean pending = payment.getStatus() == PaymentStatus.PENDING;
        payment.expireReservation();
        if (pending) {
            outbox.append(new CommerceEvent(UUID.randomUUID().toString(), "payment.failed", payment.getOrderId(),
                    payment.getUserId(), payment.getAmount(), payment.getId(), Instant.now()));
        }
        payments.flush();
    }
}
