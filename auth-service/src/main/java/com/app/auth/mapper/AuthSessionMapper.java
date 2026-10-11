package com.app.auth.mapper;

import com.app.auth.dto.response.AuthResponse;
import com.app.auth.entity.User;
import com.app.auth.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionMapper {

    private final TokenService tokens;
    private final UserMapper users;

    public AuthSessionMapper(
            TokenService tokens,
            UserMapper users
    ) {
        this.tokens = tokens;
        this.users = users;
    }

    public AuthResponse toResponse(
            User user,
            String refreshToken,
            Instant expiresAt
    ) {
        return new AuthResponse(tokens.create(user), "Bearer", tokens.expiresIn(), users.toResponse(user),
                refreshToken, Math.max(0, Duration.between(Instant.now(), expiresAt).toSeconds()));
    }
}
