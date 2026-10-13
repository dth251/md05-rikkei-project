package com.app.auth.mapper;

import com.app.auth.dto.response.NotificationRecipientResponse;
import com.app.auth.entity.User;
import org.springframework.stereotype.Component;

@Component
public class NotificationRecipientMapper {

    public NotificationRecipientResponse toResponse(
            User user
    ) {
        return new NotificationRecipientResponse(user.getId(), user.getEmail());
    }
}
