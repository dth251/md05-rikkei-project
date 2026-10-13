package com.app.auth.service;

import com.app.auth.dto.response.NotificationRecipientResponse;

public interface NotificationRecipientService {

    NotificationRecipientResponse resolve(
            Long userId,
            String credential
    );
}
