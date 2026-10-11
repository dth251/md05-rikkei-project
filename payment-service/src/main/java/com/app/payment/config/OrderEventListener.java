package com.app.payment.config;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    public OrderEventListener(
            ObjectMapper objectMapper,
            PaymentService paymentService
    ) {
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = "payment.order-created.q")
    public void receive(Message message) {
        try {
            CommerceEvent event = objectMapper.readValue(message.getBody(), CommerceEvent.class);
            if (event == null || event.eventId() == null) {
                throw new IllegalArgumentException("Event ID required");
            }
            UUID.fromString(event.eventId());
            if (!"order.created".equals(event.type()) || !event.type().equals(message.getMessageProperties().getReceivedRoutingKey())
                    || event.orderId() == null || event.orderId() <= 0 || event.userId() == null || event.userId() <= 0
                    || event.amount() == null || event.amount().signum() <= 0 || event.amount().scale() > 2
                    || event.amount().precision() - event.amount().scale() > 16 || event.occurredAt() == null) {
                throw new IllegalArgumentException("Invalid order event");
            }
            paymentService.acceptOrder(event);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid order event rejected");
        }
    }
}
