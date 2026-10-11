package com.app.auth.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user,
        String refreshToken,
        long refreshExpiresIn
) {
}
