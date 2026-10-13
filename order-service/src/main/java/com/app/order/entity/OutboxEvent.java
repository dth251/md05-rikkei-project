package com.app.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_due", columnList = "publishedAt,nextAttemptAt,createdAt"))
public class OutboxEvent {

    @Id
    @Column(length = 36)
    private String eventId;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @Column(length = 55)
    private String traceParent;

    public void setTraceParent(String value) {
        traceParent = value;
    }

    public String getTraceParent() {
        return traceParent;
    }

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int attempts;

    private Instant nextAttemptAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(
        String eventId,
        String eventType,
        String payload,
        Instant createdAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }
}
