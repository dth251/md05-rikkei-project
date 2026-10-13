package com.app.auth.controller;

import com.app.auth.dto.response.ApiResponse;
import com.app.auth.dto.response.NotificationRecipientResponse;
import com.app.auth.service.NotificationRecipientService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationRecipientController {

    private final NotificationRecipientService recipients;

    public NotificationRecipientController(
            NotificationRecipientService recipients
    ) {
        this.recipients = recipients;
    }

    @GetMapping("/internal/notification-recipients/{userId}")
    public ResponseEntity<ApiResponse<NotificationRecipientResponse>> resolve(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Notification-Credential", required = false) String credential
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(recipients.resolve(userId, credential), "Recipient resolved"));
    }
}
