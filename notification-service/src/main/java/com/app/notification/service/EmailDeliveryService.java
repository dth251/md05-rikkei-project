package com.app.notification.service;

import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.dto.response.EmailTask;
import java.util.List;

public interface EmailDeliveryService {

    void enqueue(
            CommerceEvent event
    );

    List<EmailTask> claim();

    void sent(
            EmailTask task
    );

    void failed(
            EmailTask task,
            String errorCode
    );

    void uncertain(
            EmailTask task
    );
}
