package com.timeix3.messenger.dto;

public record UserPresenceResponse(
    Long userId, String username, String displayName, PresenceStatus status) {}
