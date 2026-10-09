package com.example.shortener.link.dto;

import java.time.Instant;

public record LinkListItem(String originalUrl, String shortCode, String shortUrl,
        Instant createdAt, String status, Instant expiresAt) {}
