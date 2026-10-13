package com.app.auth.dto.response;

public record NotificationRecipientResponse(
        Long userId,
        String email
) {
}
