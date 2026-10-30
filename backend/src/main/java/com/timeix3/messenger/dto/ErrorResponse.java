package com.timeix3.messenger.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        String message,
        Map<String, String> fieldErrors
) {
}
