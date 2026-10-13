package com.app.notification.dto.response;

public record EmailRecipientResponse(
        Long userId,
        String email
) {
}
