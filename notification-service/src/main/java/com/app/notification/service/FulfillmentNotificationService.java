package com.app.notification.service;

import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.dto.response.FulfillmentResponse;

public interface FulfillmentNotificationService {

    FulfillmentResponse accept(
            CommerceEvent event
    );
}
