package com.app.order.config;

import com.app.order.dto.event.CommerceEvent;
import com.app.order.service.OrderPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private final ObjectMapper objectMapper;
    private final OrderPaymentService orderPaymentService;

    public PaymentEventListener(
            ObjectMapper objectMapper,
            OrderPaymentService orderPaymentService
    ) {
        this.objectMapper = objectMapper;
        this.orderPaymentService = orderPaymentService;
    }

    @RabbitListener(queues = "order.payment-result.q")
    public void receive(Message message) {
        try {
            CommerceEvent event = objectMapper.readValue(message.getBody(), CommerceEvent.class);
            if (event == null || event.eventId() == null) {
                throw new IllegalArgumentException("Event ID required");
            }
            UUID.fromString(event.eventId());
            if (!("payment.succeeded".equals(event.type()) || "payment.failed".equals(event.type()))
                    || !event.type().equals(message.getMessageProperties().getReceivedRoutingKey())
                    || event.orderId() == null || event.orderId() <= 0 || event.userId() == null || event.userId() <= 0
                    || event.amount() == null || event.amount().signum() <= 0 || event.occurredAt() == null
                    || event.paymentId() == null || event.paymentId() <= 0) {
                throw new IllegalArgumentException("Invalid payment event");
            }
            // Service transaction commits before this method returns and the container acknowledges.
            orderPaymentService.accept(event);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid payment event rejected");
        }
    }
}
