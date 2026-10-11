package com.app.notification.service.impl;

import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.dto.response.NotificationResponse;
import com.app.notification.mapper.NotificationMapper;
import com.app.notification.repository.EventInbox;
import com.app.notification.repository.NotificationRepository;
import com.app.notification.service.NotificationService;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final EventInbox eventInbox;
    private final JdbcTemplate jdbc;
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(
            EventInbox eventInbox,
            JdbcTemplate jdbc,
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper
    ) {
        this.eventInbox = eventInbox;
        this.jdbc = jdbc;
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    @Override
    @Transactional
    public NotificationResponse accept(CommerceEvent event) {
        if (!eventInbox.claim(event.eventId(), event.type())) {
            return notificationMapper.toResponse(notificationRepository.findByOrderId(event.orderId()).orElseThrow());
        }
        jdbc.update("INSERT INTO notification_logs (event_id, order_id, user_id, event_type, amount, payment_id, occurred_at, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (order_id) DO NOTHING",
                event.eventId(), event.orderId(), event.userId(), event.type(), event.amount(), event.paymentId(), Timestamp.from(event.occurredAt()));
        var log = notificationRepository.findByOrderId(event.orderId()).orElseThrow();
        if (!log.getEventType().equals(event.type()) || !log.getUserId().equals(event.userId())
                || log.getAmount().compareTo(event.amount()) != 0 || !log.getPaymentId().equals(event.paymentId())) {
            throw new IllegalArgumentException("Conflicting terminal order event");
        }
        return notificationMapper.toResponse(log);
    }
}
