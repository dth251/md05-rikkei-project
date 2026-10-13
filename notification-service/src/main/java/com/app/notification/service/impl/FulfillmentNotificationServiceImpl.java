package com.app.notification.service.impl;

import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.dto.response.FulfillmentResponse;
import com.app.notification.mapper.FulfillmentMapper;
import com.app.notification.repository.EventInbox;
import com.app.notification.repository.FulfillmentRepository;
import com.app.notification.service.FulfillmentNotificationService;
import com.app.notification.service.EmailDeliveryService;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentNotificationServiceImpl implements FulfillmentNotificationService {

    private final EventInbox inbox;
    private final JdbcTemplate jdbc;
    private final FulfillmentRepository logs;
    private final FulfillmentMapper mapper;
    private final EmailDeliveryService emailDeliveries;

    public FulfillmentNotificationServiceImpl(
            EventInbox inbox,
            JdbcTemplate jdbc,
            FulfillmentRepository logs,
            FulfillmentMapper mapper,
            EmailDeliveryService emailDeliveries
    ) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.logs = logs;
        this.mapper = mapper;
        this.emailDeliveries = emailDeliveries;
    }

    @Override
    @Transactional
    public FulfillmentResponse accept(
            CommerceEvent event
    ) {
        if (!("order.shipped".equals(event.type()) || "order.completed".equals(event.type()))) {
            throw new IllegalArgumentException("Unsupported fulfillment stage");
        }
        if (inbox.claim(event.eventId(), event.type())) {
            jdbc.update("INSERT INTO fulfillment_logs(event_id,order_id,user_id,event_type,amount,occurred_at,created_at) "
                    + "VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(order_id,event_type) DO NOTHING",
                    event.eventId(), event.orderId(), event.userId(), event.type(), event.amount(), Timestamp.from(event.occurredAt()));
        }
        var log = logs.findByOrderIdAndEventType(event.orderId(), event.type()).orElseThrow(() ->
                new IllegalArgumentException("Replayed event does not match recorded fulfillment stage"));
        if (!log.getUserId().equals(event.userId()) || log.getAmount().compareTo(event.amount()) != 0) {
            throw new IllegalArgumentException("Conflicting fulfillment event");
        }
        emailDeliveries.enqueue(event);
        com.app.notification.config.CommerceTrace.audit("fulfillment_accept", "completed");
        return mapper.toResponse(log);
    }
}
