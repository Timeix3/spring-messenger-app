package com.timeix3.messenger.dto.conversation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateConversationRequest(

        @NotNull(message = "Companion id is required")
        @Positive(message = "Companion id must be positive")
        Long companionId
) {}
