package com.timeix3.messenger.dto.user;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        Instant createdAt
) {}
