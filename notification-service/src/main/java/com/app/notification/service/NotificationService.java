package com.app.notification.service;

import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.dto.response.NotificationResponse;

public interface NotificationService {

    NotificationResponse accept(CommerceEvent event);
}
