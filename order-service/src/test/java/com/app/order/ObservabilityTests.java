package com.app.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.app.order.config.BacklogMetrics;
import com.app.order.config.CommerceTrace;
import com.app.order.entity.OutboxEvent;
import com.app.order.dto.event.CommerceEvent;
import com.app.order.service.impl.OutboxServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ObservabilityTests {

    @Test
    void capturesOnlyTraceIdentityInsideOutboxTransaction() {
        var entityManager = mock(EntityManager.class);
        var outbox = new OutboxServiceImpl(entityManager, new ObjectMapper().findAndRegisterModules(),
                mock(JdbcTemplate.class), mock(RabbitTemplate.class), 50);
        var identity = SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef",
                TraceFlags.getSampled(), TraceState.getDefault());
        try (var scope = Span.wrap(identity).makeCurrent()) {
            outbox.append(new CommerceEvent(UUID.randomUUID().toString(), "order.created", 1L, 2L,
                    new java.math.BigDecimal("12.50"), null, Instant.now()));
        }
        var record = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(entityManager).persist(record.capture());
        assertEquals("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", record.getValue().getTraceParent());
        assertNull(CommerceTrace.capture());
        assertThrows(IllegalArgumentException.class, () -> CommerceTrace.audit("customer@example.com", "ok"));
    }

    @Test
    void gaugesMeasurePendingRowsAndInvalidateSnapshotWhenDatabaseFails() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:metrics-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE outbox_events(created_at TIMESTAMP,published_at TIMESTAMP)");
        jdbc.update("INSERT INTO outbox_events VALUES (?,NULL)", Timestamp.from(Instant.now().minusSeconds(180)));
        jdbc.update("INSERT INTO outbox_events VALUES (?,?)", Timestamp.from(Instant.now().minusSeconds(200)), Timestamp.from(Instant.now()));
        var registry = new SimpleMeterRegistry();
        var metrics = new BacklogMetrics(jdbc, registry);
        metrics.sample();
        assertEquals(1, registry.get("commerce.outbox.pending").gauge().value());
        assertTrue(registry.get("commerce.outbox.oldest.seconds").gauge().value() >= 180);
        assertEquals(1, registry.get("commerce.backlog.snapshot.valid").gauge().value());
        jdbc.execute("DROP TABLE outbox_events");
        metrics.sample();
        assertEquals(0, registry.get("commerce.backlog.snapshot.valid").gauge().value());
        assertEquals(1, registry.get("commerce.outbox.pending").gauge().value(), "Failure must not masquerade as an empty backlog");
    }
}
