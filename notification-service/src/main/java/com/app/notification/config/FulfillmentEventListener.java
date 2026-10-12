package com.app.notification.config;

import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.service.FulfillmentNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentEventListener {

    private final ObjectMapper json;
    private final FulfillmentNotificationService notifications;

    public FulfillmentEventListener(
            ObjectMapper json,
            FulfillmentNotificationService notifications
    ) {
        this.json = json;
        this.notifications = notifications;
    }

    @RabbitListener(queues = "notification.fulfillment.q")
    public void receive(
            Message message
    ) {
        try {
            CommerceEvent event = json.readValue(message.getBody(), CommerceEvent.class);
            if (event == null || event.eventId() == null) {
                throw new IllegalArgumentException("Event ID required");
            }
            UUID.fromString(event.eventId());
            if (!("order.shipped".equals(event.type()) || "order.completed".equals(event.type()))
                    || !event.type().equals(message.getMessageProperties().getReceivedRoutingKey())
                    || event.orderId() == null || event.orderId() <= 0 || event.userId() == null || event.userId() <= 0
                    || event.amount() == null || event.amount().signum() <= 0 || event.amount().scale() > 2
                    || event.amount().precision() - event.amount().scale() > 16 || event.occurredAt() == null
                    || event.paymentId() != null) {
                throw new IllegalArgumentException("Invalid fulfillment event");
            }
            notifications.accept(event);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid fulfillment event rejected");
        }
    }
}
