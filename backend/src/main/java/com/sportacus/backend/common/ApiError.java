package com.sportacus.backend.common;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        List<String> messages
) {
    public static ApiError of(int status, String error, List<String> messages) {
        return new ApiError(OffsetDateTime.now(), status, error, messages);
    }
}
