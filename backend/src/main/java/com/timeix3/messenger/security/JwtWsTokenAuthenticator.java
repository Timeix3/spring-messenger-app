package com.timeix3.messenger.security;

import com.timeix3.messenger.exception.UnauthorizedException;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JwtWsTokenAuthenticator {

    private final JwtService jwtService;

    public Authentication authenticate(String token) {
        if (!StringUtils.hasText(token)) {
            throw new UnauthorizedException("JWT token is missing");
        }

        if (!jwtService.isTokenValid(token)) {
            throw new UnauthorizedException("Invalid JWT token");
        }
        Claims claims = jwtService.parseClaims(token);
        Long userId = Long.valueOf(claims.getSubject());

        return new UsernamePasswordAuthenticationToken(userId, null);
    }
}