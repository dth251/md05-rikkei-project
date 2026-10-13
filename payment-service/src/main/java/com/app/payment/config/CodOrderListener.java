package com.app.payment.config;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.service.CodPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CodOrderListener {

    private final ObjectMapper json;
    private final CodPaymentService payments;

    public CodOrderListener(ObjectMapper json, CodPaymentService payments) {
        this.json = json;
        this.payments = payments;
    }

    @RabbitListener(queues = "payment.cod-lifecycle.q")
    public void receive(Message message) {
        try {
            CommerceEvent event = json.readValue(message.getBody(), CommerceEvent.class);
            if (event == null || event.eventId() == null) {
                throw new IllegalArgumentException("Event required");
            }
            UUID.fromString(event.eventId());
            if (!("order.cod-shipped".equals(event.type()) || "order.cod-cancelled".equals(event.type()))
                    || !event.type().equals(message.getMessageProperties().getReceivedRoutingKey())
                    || event.orderId() == null || event.orderId() <= 0 || event.userId() == null || event.userId() <= 0
                    || event.amount() == null || event.amount().signum() <= 0 || event.amount().scale() > 2
                    || event.amount().precision() - event.amount().scale() > 16 || event.occurredAt() == null
                    || event.paymentId() != null) {
                throw new IllegalArgumentException("Invalid COD lifecycle event");
            }
            payments.accept(event);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid COD lifecycle event rejected");
        }
    }
}
