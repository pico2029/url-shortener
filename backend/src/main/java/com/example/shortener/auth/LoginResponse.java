package com.example.shortener.auth;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt) {}
