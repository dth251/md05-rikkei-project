package com.app.payment.config;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.service.PaymentExpiryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderExpiryListener {

    private final ObjectMapper json;
    private final PaymentExpiryService expiry;

    public OrderExpiryListener(ObjectMapper json, PaymentExpiryService expiry) {
        this.json = json;
        this.expiry = expiry;
    }

    @RabbitListener(queues = "payment.order-expired.q")
    public void receive(Message message) {
        try {
            CommerceEvent event = json.readValue(message.getBody(), CommerceEvent.class);
            if (event == null || event.eventId() == null) {
                throw new IllegalArgumentException("Event required");
            }
            UUID.fromString(event.eventId());
            if (!("order.expired".equals(event.type()) || "order.refund-requested".equals(event.type()))
                    || !event.type().equals(message.getMessageProperties().getReceivedRoutingKey())
                    || event.orderId() == null || event.orderId() <= 0 || event.userId() == null || event.userId() <= 0
                    || event.amount() == null || event.amount().signum() <= 0 || event.amount().scale() > 2
                    || event.amount().precision() - event.amount().scale() > 16 || event.occurredAt() == null
                    || ("order.refund-requested".equals(event.type()) && (event.paymentId() == null || event.paymentId() <= 0))) {
                throw new IllegalArgumentException("Invalid expiration event");
            }
            expiry.accept(event);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid expiration event rejected");
        }
    }
}
