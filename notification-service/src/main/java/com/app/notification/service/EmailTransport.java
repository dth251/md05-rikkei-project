package com.app.notification.service;

import com.app.notification.dto.response.EmailTask;

public interface EmailTransport {

    void send(
            EmailTask task,
            String recipient
    );
}
