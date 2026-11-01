package com.timeix3.messenger.dto.message;

import com.timeix3.messenger.dto.user.UserResponse;
import java.time.Instant;

public record MessageResponse(
    Long id, Long conversationId, UserResponse sender, String content, Instant createdAt) {}
