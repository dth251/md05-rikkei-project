package com.app.gateway.dto.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Object errors,
        Instant timestamp
) {

    public static ApiResponse<Void> error(
            String message
    ) {
        return new ApiResponse<>(false, message, null, java.util.Map.of(), Instant.now());
    }
}
