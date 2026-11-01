package com.timeix3.messenger.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
    @NotBlank(message = "Message content must not be empty")
        @Size(
            min = 1,
            max = 4000,
            message = "Message content must be between 1 and 4000 characters")
        String content) {}
