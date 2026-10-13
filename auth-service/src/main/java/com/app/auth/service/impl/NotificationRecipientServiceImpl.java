package com.app.auth.service.impl;

import com.app.auth.config.exception.ApiException;
import com.app.auth.dto.response.NotificationRecipientResponse;
import com.app.auth.mapper.NotificationRecipientMapper;
import com.app.auth.repository.UserRepository;
import com.app.auth.service.NotificationRecipientService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRecipientServiceImpl implements NotificationRecipientService {

    private final UserRepository users;
    private final NotificationRecipientMapper mapper;
    private final String secret;

    public NotificationRecipientServiceImpl(
            UserRepository users,
            NotificationRecipientMapper mapper,
            @Value("${notification.recipient-token:}") String secret
    ) {
        this.users = users;
        this.mapper = mapper;
        this.secret = secret;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationRecipientResponse resolve(
            Long userId,
            String credential
    ) {
        if (secret.length() < 32 || credential == null || credential.length() > 256
                || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                credential.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Internal credential required");
        }
        return mapper.toResponse(users.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Recipient not found")));
    }
}
