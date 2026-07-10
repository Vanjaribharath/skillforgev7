package com.executionos.service;

import com.executionos.dto.AuthDtos.*;
import com.executionos.model.RefreshToken;
import com.executionos.model.User;
import com.executionos.repository.RefreshTokenRepository;
import com.executionos.repository.UserRepository;
import com.executionos.security.JwtService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshDays;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, @Value("${executionos.jwt.refresh-token-days}") long refreshDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setName(request.name());
        user.setTimezone(request.timezone() == null ? "UTC" : request.timezone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return tokens(users.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return tokens(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken token = refreshTokens.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        token.setRevoked(true);
        return tokens(token.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByToken(refreshToken).ifPresent(token -> token.setRevoked(true));
    }

    public UserResponse me(String email) {
        return users.findByEmail(email).map(this::toResponse).orElseThrow();
    }

    private TokenResponse tokens(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshDays * 24 * 60 * 60));
        refreshTokens.save(refreshToken);
        return new TokenResponse(jwtService.issueAccessToken(user), refreshToken.getToken(), toResponse(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getAvatarUrl(),
                user.getRole().name(), user.getTimezone());
    }
}
