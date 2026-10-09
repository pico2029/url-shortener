package com.example.shortener.link;

import java.time.Instant;

public record CreateLinkResponse(String originalUrl, String shortCode, String shortUrl, Instant expiresAt) {
    public CreateLinkResponse(String originalUrl, String shortCode, String shortUrl) {
        this(originalUrl, shortCode, shortUrl, null);
    }
}
