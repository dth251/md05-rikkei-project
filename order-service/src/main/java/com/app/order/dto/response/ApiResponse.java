package com.app.order.dto.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Object errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(
            T data,
            String message
    ) {
        return new ApiResponse<>(
                true,
                message,
                data,
                null,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> error(
            Object errors,
            String message
    ) {
        return new ApiResponse<>(
                false,
                message,
                null,
                errors,
                Instant.now()
        );
    }
}
