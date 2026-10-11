package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.app.order.dto.event.CommerceEvent;
import com.app.order.service.OutboxService;
import com.app.order.config.OutboxRelay;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class OutboxTests {

    @Autowired
    private OutboxService outbox;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private RabbitTemplate rabbit;

    @BeforeEach
    void isolateOutbox() {
        jdbc.update("DELETE FROM outbox_events");
    }

    @Test
    void drainsMultipleBatchesWithoutBlockingBehindFailedEvent() {
        CommerceEvent failed = event();
        outbox.append(failed);
        for (int index = 0; index < 101; index++) {
            outbox.append(event());
        }
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(
                    !failed.eventId().equals(correlation.getId()), null));
            return null;
        }).when(rabbit).send(eq("commerce.events"), eq("order.created"), any(Message.class), any(CorrelationData.class));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new OutboxRelay(outbox, registry, 200, 10000).relay();
        assertEquals(101, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE event_id=?", Integer.class, failed.eventId()));
        assertEquals(0, outbox.pending().size());
        assertEquals(1.0, registry.counter("commerce.outbox.publish", "result", "failed").count());
        jdbc.update("UPDATE outbox_events SET next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=?", failed.eventId());
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("commerce.events"), eq("order.created"), any(Message.class), any(CorrelationData.class));
        new OutboxRelay(outbox, registry, 200, 10000).relay();
        assertEquals(102, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
    }

    @Test
    void drainHonorsMaximumEventsAndBackoffIsBounded() {
        for (int index = 0; index < 5; index++) {
            outbox.append(event());
        }
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("commerce.events"), eq("order.created"), any(Message.class), any(CorrelationData.class));
        new OutboxRelay(outbox, new SimpleMeterRegistry(), 2, 10000).relay();
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class));
        String eventId = outbox.pending().getFirst();
        for (int index = 0; index < 40; index++) {
            outbox.defer(eventId);
        }
        assertEquals(30, jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE event_id=?", Integer.class, eventId));
        Instant due = jdbc.queryForObject("SELECT next_attempt_at FROM outbox_events WHERE event_id=?",
                (row, index) -> row.getTimestamp(1).toInstant(), eventId);
        org.junit.jupiter.api.Assertions.assertTrue(due.isAfter(Instant.now().plusSeconds(290)));
        org.junit.jupiter.api.Assertions.assertTrue(due.isBefore(Instant.now().plusSeconds(301)));
    }

    @Test
    void marksPublishedOnlyAfterPersistentMessageIsAcknowledged() {
        CommerceEvent event = event();
        outbox.append(event);
        doAnswer(call -> {
            Message message = call.getArgument(2);
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            assertEquals(event.eventId(), message.getMessageProperties().getMessageId());
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("commerce.events"), eq("order.created"), any(Message.class), any(CorrelationData.class));
        outbox.publish(event.eventId());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_id=? AND published_at IS NOT NULL",
                Integer.class, event.eventId()));
    }

    @Test
    void brokerNackLeavesEventPendingForRetry() {
        CommerceEvent event = event();
        outbox.append(event);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "not accepted"));
            return null;
        }).when(rabbit).send(eq("commerce.events"), eq("order.created"), any(Message.class), any(CorrelationData.class));
        assertThrows(IllegalStateException.class, () -> outbox.publish(event.eventId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_id=? AND published_at IS NULL",
                Integer.class, event.eventId()));
    }

    private CommerceEvent event() {
        return new CommerceEvent(UUID.randomUUID().toString(), "order.created", 1L, 1L,
                BigDecimal.TEN, null, Instant.now());
    }

    @Test
    void brokerAckWithUnroutableReturnDoesNotMarkPublished() {
        CommerceEvent event = event();
        outbox.append(event);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312,
                    "NO_ROUTE", "commerce.events", "order.created"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("commerce.events"), eq("order.created"), any(Message.class), any(CorrelationData.class));
        assertThrows(IllegalStateException.class, () -> outbox.publish(event.eventId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_id=? AND published_at IS NULL",
                Integer.class, event.eventId()));
    }
}
