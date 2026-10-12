package com.app.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.app.notification.config.FulfillmentEventListener;
import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.entity.FulfillmentLog;
import com.app.notification.mapper.FulfillmentMapper;
import com.app.notification.repository.FulfillmentRepository;
import com.app.notification.service.FulfillmentNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FulfillmentNotificationTests {

    private static final AtomicLong ORDERS = new AtomicLong(990000);

    @Autowired
    private FulfillmentRepository logs;
    @Autowired
    private FulfillmentMapper mapper;
    @Autowired
    private FulfillmentEventListener listener;
    @Autowired
    private ObjectMapper json;
    @MockitoBean
    private FulfillmentNotificationService notifications;

    @Test
    void separateStagesHaveIndependentUniqueConstraints() {
        long order = ORDERS.incrementAndGet();
        logs.saveAndFlush(new FulfillmentLog(event(order, "order.shipped")));
        logs.saveAndFlush(new FulfillmentLog(event(order, "order.completed")));
        assertThrows(DataIntegrityViolationException.class, () ->
                logs.saveAndFlush(new FulfillmentLog(event(order, "order.shipped"))));
        assertEquals("order.shipped", mapper.toResponse(logs.findByOrderIdAndEventType(order, "order.shipped")
                .orElseThrow()).eventType());
        assertEquals("order.completed", mapper.toResponse(logs.findByOrderIdAndEventType(order, "order.completed")
                .orElseThrow()).eventType());
    }

    @Test
    void validEnvelopeNeedsNoPaymentIdAndReachesOwnedService() throws Exception {
        CommerceEvent event = event(ORDERS.incrementAndGet(), "order.shipped");
        listener.receive(message(event, event.type()));
        verify(notifications).accept(event);
    }

    @Test
    void malformedWrongRouteAndTerminalEventAreRejected() throws Exception {
        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                listener.receive(new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties())));
        CommerceEvent valid = event(ORDERS.incrementAndGet(), "order.shipped");
        Message wrongRoute = message(valid, "order.completed");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(wrongRoute));
        Message terminal = message(event(ORDERS.incrementAndGet(), "order.confirmed"), "order.confirmed");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(terminal));
        CommerceEvent invalidPayment = new CommerceEvent(UUID.randomUUID().toString(), "order.shipped", 1L, 1L,
                BigDecimal.TEN, 1L, Instant.now());
        Message invalid = message(invalidPayment, "order.shipped");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(invalid));
    }

    private CommerceEvent event(
            long order,
            String type
    ) {
        return new CommerceEvent(UUID.randomUUID().toString(), type, order, 1L, BigDecimal.TEN, null, Instant.now());
    }

    private Message message(
            CommerceEvent event,
            String route
    ) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(route);
        return new Message(json.writeValueAsBytes(event), properties);
    }
}
