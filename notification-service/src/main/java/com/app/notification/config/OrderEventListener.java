package com.app.notification.config;

import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public OrderEventListener(
            ObjectMapper objectMapper,
            NotificationService notificationService
    ) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = {"notification.order.q", "notification.expiry.q"})
    public void receive(Message message) {
        try {
            CommerceEvent event = objectMapper.readValue(message.getBody(), CommerceEvent.class);
            if (event == null || event.eventId() == null) {
                throw new IllegalArgumentException("Event ID required");
            }
            UUID.fromString(event.eventId());
            boolean expired = "order.expired".equals(event.type());
            if (!("order.confirmed".equals(event.type()) || "order.cancelled".equals(event.type()) || expired)
                    || !event.type().equals(message.getMessageProperties().getReceivedRoutingKey())
                    || event.orderId() == null || event.orderId() <= 0 || event.userId() == null || event.userId() <= 0
                    || event.amount() == null || event.amount().signum() <= 0 || event.amount().scale() > 2
                    || event.amount().precision() - event.amount().scale() > 16 || event.occurredAt() == null
                    || (expired ? event.paymentId() != null : event.paymentId() == null || event.paymentId() <= 0)) {
                throw new IllegalArgumentException("Invalid terminal order event");
            }
            notificationService.accept(event);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid terminal order event rejected");
        }
    }
}
