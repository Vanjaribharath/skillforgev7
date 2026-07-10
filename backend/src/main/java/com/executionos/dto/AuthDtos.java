package com.executionos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(@Email String email, @NotBlank String name, @NotBlank String password, String timezone) {}
    public record LoginRequest(@Email String email, @NotBlank String password) {}
    public record TokenResponse(String accessToken, String refreshToken, UserResponse user) {}
    public record UserResponse(UUID id, String email, String name, String avatarUrl, String role, String timezone) {}
}
