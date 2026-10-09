package com.example.shortener.auth.dto;

import com.example.shortener.user.entity.User;
import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(UUID id, String email, Instant createdAt) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}
