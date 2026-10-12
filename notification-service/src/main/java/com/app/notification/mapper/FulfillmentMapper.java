package com.app.notification.mapper;

import com.app.notification.dto.response.FulfillmentResponse;
import com.app.notification.entity.FulfillmentLog;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentMapper {

    public FulfillmentResponse toResponse(
            FulfillmentLog log
    ) {
        return new FulfillmentResponse(log.getEventId(), log.getOrderId(), log.getUserId(), log.getEventType(),
                log.getAmount(), log.getOccurredAt(), log.getCreatedAt());
    }
}
