package com.app.order.service.impl;

import com.app.order.dto.event.CommerceEvent;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.PaymentMethod;
import com.app.order.repository.EventInbox;
import com.app.order.repository.OrderRepository;
import com.app.order.repository.StockRepository;
import com.app.order.service.OrderPaymentService;
import com.app.order.service.OutboxService;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final EventInbox eventInbox;
    private final OrderRepository orderRepository;
    private final StockRepository stockRepository;
    private final OutboxService outboxService;

    public OrderPaymentServiceImpl(
            EventInbox eventInbox,
            OrderRepository orderRepository,
            StockRepository stockRepository,
            OutboxService outboxService
    ) {
        this.eventInbox = eventInbox;
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public void accept(CommerceEvent event) {
        if (!eventInbox.claim(event.eventId(), event.type())) {
            return;
        }
        PurchaseOrder order = orderRepository.findLockedById(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown order in payment event"));
        if (!order.getUserId().equals(event.userId()) || order.getTotalAmount().compareTo(event.amount()) != 0) {
            throw new IllegalArgumentException("Payment event does not match order");
        }
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            if ("payment.succeeded".equals(event.type())) {
                if (order.getCodPaymentId() != null) {
                    if (!order.getCodPaymentId().equals(event.paymentId())) {
                        throw new IllegalArgumentException("COD payment identity changed");
                    }
                    return;
                }
                if (order.getStatus() != OrderStatus.SHIPPING) {
                    throw new IllegalArgumentException("COD collection requires shipped order");
                }
                order.recordCodPayment(event.paymentId(), event.occurredAt());
                outboxService.append(new CommerceEvent(UUID.randomUUID().toString(), "order.confirmed",
                        order.getId(), order.getUserId(), order.getTotalAmount(), event.paymentId(), Instant.now()));
            } else if (order.getStatus() == OrderStatus.CANCELLED) {
                outboxService.append(new CommerceEvent(UUID.randomUUID().toString(), "order.cancelled",
                        order.getId(), order.getUserId(), order.getTotalAmount(), event.paymentId(), Instant.now()));
            } else {
                throw new IllegalArgumentException("Unexpected COD payment failure");
            }
            orderRepository.flush();
            return;
        }
        if (order.getStatus() == OrderStatus.EXPIRED && "payment.succeeded".equals(event.type())) {
            // Expiration already released stock. Never confirm or reserve again after late payment.
            outboxService.append(new CommerceEvent(UUID.randomUUID().toString(), "order.refund-requested",
                    order.getId(), order.getUserId(), order.getTotalAmount(), event.paymentId(), Instant.now()));
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        boolean successful = "payment.succeeded".equals(event.type());
        for (var item : order.getItems().stream().sorted(Comparator.comparing(item -> item.getProductId())).toList()) {
            if (stockRepository.finalizeReservation(item.getProductId(), item.getQuantity(), successful) != 1) {
                throw new IllegalStateException("Reserved stock inconsistent; payment event will retry");
            }
        }
        order.completePayment(successful);
        outboxService.append(new CommerceEvent(UUID.randomUUID().toString(),
                successful ? "order.confirmed" : "order.cancelled", order.getId(), order.getUserId(),
                order.getTotalAmount(), event.paymentId(), Instant.now()));
        orderRepository.flush();
    }
}
