package com.timeix3.messenger.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Username must not be blank")
        @Size(min = 3, max = 32, message = "Username must be between 3 and 32 characters")
        @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "Username can contain only letters, digits and underscore")
        String username,
    @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,
    @Size(max = 64, message = "Display name must not exceed 64 characters") String displayName) {}
