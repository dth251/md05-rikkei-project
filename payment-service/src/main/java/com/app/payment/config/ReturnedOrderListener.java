package com.app.payment.config;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.service.ReturnRefundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReturnedOrderListener {

    private final ObjectMapper json;
    private final ReturnRefundService refunds;

    public ReturnedOrderListener(
            ObjectMapper json,
            ReturnRefundService refunds
    ) {
        this.json = json;
        this.refunds = refunds;
    }

    @RabbitListener(queues = "payment.return-received.q")
    public void receive(Message message) {
        try {
            CommerceEvent event = json.readValue(message.getBody(), CommerceEvent.class);
            if (event == null || event.eventId() == null) {
                throw new IllegalArgumentException("Event required");
            }
            UUID.fromString(event.eventId());
            if (!"order.return-received".equals(event.type())
                    || !event.type().equals(message.getMessageProperties().getReceivedRoutingKey())
                    || event.orderId() == null || event.orderId() <= 0 || event.userId() == null || event.userId() <= 0
                    || event.amount() == null || event.amount().signum() <= 0 || event.amount().scale() > 2
                    || event.amount().precision() - event.amount().scale() > 16 || event.occurredAt() == null
                    || (event.paymentId() != null && event.paymentId() <= 0)) {
                throw new IllegalArgumentException("Invalid return receipt event");
            }
            refunds.accept(event);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid return receipt event rejected");
        }
    }
}
