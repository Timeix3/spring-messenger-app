package com.timeix3.messenger.dto.message;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MarkReadRequest(
    @NotNull(message = "Message id is required") @Positive(message = "Message id must be positive")
        Long messageId) {}
