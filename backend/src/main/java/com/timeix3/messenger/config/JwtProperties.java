package com.timeix3.messenger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secretBase64,
        Duration accessExpiration
) {
}
