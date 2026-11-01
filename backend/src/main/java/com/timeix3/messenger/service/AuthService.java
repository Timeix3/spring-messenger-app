package com.timeix3.messenger.service;

import com.timeix3.messenger.config.JwtProperties;
import com.timeix3.messenger.dto.auth.AuthResponse;
import com.timeix3.messenger.dto.auth.LoginRequest;
import com.timeix3.messenger.dto.auth.RegisterRequest;
import com.timeix3.messenger.entity.User;
import com.timeix3.messenger.exception.UsernameAlreadyExistsException;
import com.timeix3.messenger.mapper.UserMapper;
import com.timeix3.messenger.repository.UserRepository;
import com.timeix3.messenger.security.JwtService;
import com.timeix3.messenger.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String username = normalizeUsername(request.username());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new UsernameAlreadyExistsException("Username is already taken");
        }

        User user = userMapper.toEntity(request);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        if (StringUtils.hasText(request.displayName())) {
            user.setDisplayName(request.displayName().trim());
        }

        User savedUser = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(
                savedUser.getId(),
                savedUser.getUsername()
        );

        return userMapper.toAuthResponse(
                savedUser,
                "Bearer",
                accessToken,
                jwtProperties.accessExpiration().toSeconds()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        String username = normalizeUsername(request.username());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        username,
                        request.password()
                )
        );

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getUsername()
        );

        return userMapper.toAuthResponse(
                user,
                "Bearer",
                accessToken,
                jwtProperties.accessExpiration().toSeconds()
        );
    }

    public void logout(String accessToken) {
        Claims claims = jwtService.parseClaims(accessToken);
        tokenBlacklistService.blacklist(claims);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
