package com.app.notification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.app.notification.config.OrderEventListener;
import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ExpiredOrderListenerTests {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final NotificationService notifications = mock(NotificationService.class);
    private final OrderEventListener listener = new OrderEventListener(json, notifications);

    @Test
    void expiryWithoutPaymentIsDeliveredToNotificationService() throws Exception {
        CommerceEvent event = expired(null);
        listener.receive(message(event, "order.expired"));
        verify(notifications).accept(event);
    }

    @Test
    void mismatchedRoutingAndUnexpectedPaymentAreRejected() throws Exception {
        Message wrongRoute = message(expired(null), "order.cancelled");
        Message wrongPayment = message(expired(7L), "order.expired");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(wrongRoute));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(wrongPayment));
        verifyNoInteractions(notifications);
    }

    private CommerceEvent expired(Long paymentId) {
        return new CommerceEvent(UUID.randomUUID().toString(), "order.expired", 10L,
                1L, BigDecimal.TEN, paymentId, Instant.now());
    }

    private Message message(CommerceEvent event, String routingKey) throws Exception {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(json.writeValueAsBytes(event), properties);
    }
}
