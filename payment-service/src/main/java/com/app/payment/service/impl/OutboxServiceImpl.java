package com.app.payment.service.impl;

import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.entity.OutboxEvent;
import com.app.payment.service.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxServiceImpl implements OutboxService {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;

    public OutboxServiceImpl(
        EntityManager entityManager,
        ObjectMapper objectMapper,
        JdbcTemplate jdbc,
        RabbitTemplate rabbitTemplate,
        @Value("${commerce.outbox.batch-size:50}") int batchSize
    ) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.batchSize = Math.clamp(batchSize, 1, 500);
    }

    @Override
    @Transactional
    public void append(CommerceEvent event) {
        try {
            entityManager.persist(new OutboxEvent(event.eventId(), event.type(),
                    objectMapper.writeValueAsString(event), event.occurredAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize commerce event", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> pending() {
        return jdbc.queryForList("SELECT event_id FROM outbox_events WHERE published_at IS NULL "
                + "AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP) "
                + "ORDER BY created_at, event_id LIMIT ?", String.class, batchSize);
    }

    @Override
    @Transactional
    public void publish(String eventId) {
        var records = jdbc.query("SELECT event_type, payload FROM outbox_events WHERE event_id=? "
                + "AND published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP) "
                + "FOR UPDATE SKIP LOCKED",
                (row, index) -> new String[]{row.getString(1), row.getString(2)}, eventId);
        if (records.isEmpty()) {
            return;
        }
        var record = records.getFirst();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(eventId);
        CorrelationData correlation = new CorrelationData(eventId);
        rabbitTemplate.send("commerce.events", record[0],
                new Message(record[1].getBytes(StandardCharsets.UTF_8), properties), correlation);
        try {
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("Broker did not accept routed event");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publish interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("Broker confirmation unavailable", exception);
        }
        // A crash after broker ACK may redeliver this event. Consumers must deduplicate eventId.
        jdbc.update("UPDATE outbox_events SET published_at=CURRENT_TIMESTAMP, next_attempt_at=NULL WHERE event_id=?", eventId);
    }

    @Override
    @Transactional
    public void defer(String eventId) {
        var attempts = jdbc.queryForList("SELECT attempts FROM outbox_events WHERE event_id=? "
                + "AND published_at IS NULL FOR UPDATE", Integer.class, eventId);
        if (attempts.isEmpty()) {
            return;
        }
        int attempt = Math.min(30, attempts.getFirst() + 1);
        long seconds = Math.min(300L, 1L << Math.min(attempt, 9));
        jdbc.update("UPDATE outbox_events SET attempts=?, next_attempt_at=? "
                + "WHERE event_id=? AND published_at IS NULL", attempt,
                Timestamp.from(Instant.now().plusSeconds(seconds)), eventId);
    }
}
