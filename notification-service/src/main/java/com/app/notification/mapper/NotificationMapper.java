package com.app.notification.mapper;

import com.app.notification.dto.response.NotificationResponse;
import com.app.notification.entity.NotificationLog;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(NotificationLog log) {
        return new NotificationResponse(log.getEventId(), log.getOrderId(), log.getUserId(), log.getEventType(), log.getCreatedAt());
    }
}
