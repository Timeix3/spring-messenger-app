package com.timeix3.messenger.dto.auth;

public record AuthResponse(
    String tokenType,
    String accessToken,
    long expiresInSeconds,
    Long userId,
    String username,
    String displayName) {}
