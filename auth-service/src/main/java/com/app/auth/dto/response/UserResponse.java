package com.app.auth.dto.response;

import com.app.auth.entity.Role;
import java.time.Instant;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        Instant createdAt
) {
}
