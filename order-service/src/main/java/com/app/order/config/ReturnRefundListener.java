package com.app.order.config;

import com.app.order.dto.event.CommerceEvent;
import com.app.order.service.OrderReturnService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReturnRefundListener {

    private final ObjectMapper json;
    private final OrderReturnService returns;

    public ReturnRefundListener(
            ObjectMapper json,
            OrderReturnService returns
    ) {
        this.json = json;
        this.returns = returns;
    }

    @RabbitListener(queues = "order.return-result.q")
    public void receive(Message message) {
        try {
            CommerceEvent event = json.readValue(message.getBody(), CommerceEvent.class);
            if (event == null || event.eventId() == null) {
                throw new IllegalArgumentException("Event required");
            }
            UUID.fromString(event.eventId());
            if (!("payment.return-refund-ready".equals(event.type()) || "payment.return-refunded".equals(event.type()))
                    || !event.type().equals(message.getMessageProperties().getReceivedRoutingKey())
                    || event.orderId() == null || event.orderId() <= 0 || event.userId() == null || event.userId() <= 0
                    || event.amount() == null || event.amount().signum() <= 0 || event.amount().scale() > 2
                    || event.amount().precision() - event.amount().scale() > 16 || event.paymentId() == null
                    || event.paymentId() <= 0 || event.occurredAt() == null) {
                throw new IllegalArgumentException("Invalid return refund event");
            }
            returns.acceptRefund(event);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid return refund event rejected");
        }
    }
}
