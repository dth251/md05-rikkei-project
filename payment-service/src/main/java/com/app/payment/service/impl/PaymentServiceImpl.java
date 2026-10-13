package com.app.payment.service.impl;

import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.response.PaymentResponse;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.PaymentMethod;
import com.app.payment.service.CodPaymentService;
import com.app.payment.mapper.PaymentMapper;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.repository.PaymentOrderLock;
import com.app.payment.repository.VnpayPaymentRepository;
import com.app.payment.service.OutboxService;
import com.app.payment.service.PaymentService;
import com.app.payment.service.PaymentExpiryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbc;
    private final PaymentOrderLock orderLocks;
    private final PaymentExpiryService expiry;
    private final VnpayPaymentRepository providerPayments;
    private final CodPaymentService cod;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper,
            OutboxService outboxService,
            JdbcTemplate jdbc,
            PaymentOrderLock orderLocks,
            PaymentExpiryService expiry,
            VnpayPaymentRepository providerPayments,
            CodPaymentService cod
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.outboxService = outboxService;
        this.jdbc = jdbc;
        this.orderLocks = orderLocks;
        this.expiry = expiry;
        this.providerPayments = providerPayments;
        this.cod = cod;
    }

    @Override
    @Transactional
    public void acceptOrder(CommerceEvent event) {
        orderLocks.lock(event.orderId());
        int claimed = jdbc.update("INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (event_id) DO NOTHING", event.eventId(), event.type());
        if (claimed == 0) {
            return;
        }
        PaymentMethod method = "order.cod-created".equals(event.type()) ? PaymentMethod.COD : PaymentMethod.ONLINE;
        jdbc.update("INSERT INTO payments (order_id, user_id, amount, payment_method, status, created_at, version) "
                + "VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, 0) ON CONFLICT (order_id) DO NOTHING",
                event.orderId(), event.userId(), event.amount(), method.name());
        Payment payment = paymentRepository.findByOrderId(event.orderId()).orElseThrow();
        if (!payment.getUserId().equals(event.userId()) || payment.getAmount().compareTo(event.amount()) != 0
                || payment.getPaymentMethod() != method) {
            throw new IllegalArgumentException("Conflicting order event");
        }
        if (method == PaymentMethod.COD) {
            expiry.apply(payment); // Reject any inconsistent online-expiration marker for this order.
            cod.apply(payment);
        } else {
            expiry.apply(payment);
            cod.apply(payment); // Reject a conflicting COD marker rather than silently accepting it.
        }
    }

    @Override
    public PaymentResponse get(
            Long id,
            Long userId,
            boolean administrator
    ) {
        return visible(paymentRepository.findById(id).orElseThrow(this::notFound), userId, administrator);
    }

    @Override
    public PaymentResponse getByOrder(
            Long orderId,
            Long userId,
            boolean administrator
    ) {
        return visible(paymentRepository.findByOrderId(orderId).orElseThrow(this::notFound), userId, administrator);
    }

    @Override
    @Transactional
    public PaymentResponse simulate(
            Long id,
            PaymentStatus status
    ) {
        if (status == null || status == PaymentStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose SUCCESS or FAILED");
        }
        Payment payment = paymentRepository.findLockedById(id).orElseThrow(this::notFound);
        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            throw new ApiException(HttpStatus.CONFLICT, "COD requires recorded cash collection");
        }
        if (providerPayments.existsById(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Provider payment requires verified callback");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            if (payment.getStatus() != status) {
                throw new ApiException(HttpStatus.CONFLICT, "Payment already completed with another outcome");
            }
            return paymentMapper.toResponse(payment);
        }
        payment.complete(status);
        outboxService.append(new CommerceEvent(UUID.randomUUID().toString(),
                status == PaymentStatus.SUCCESS ? "payment.succeeded" : "payment.failed", payment.getOrderId(),
                payment.getUserId(), payment.getAmount(), payment.getId(), Instant.now()));
        paymentRepository.flush();
        return paymentMapper.toResponse(payment);
    }

    private PaymentResponse visible(
            Payment payment,
            Long userId,
            boolean administrator
    ) {
        if (!administrator && !payment.getUserId().equals(userId)) {
            throw notFound();
        }
        return paymentMapper.toResponse(payment);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Payment not found");
    }
}
