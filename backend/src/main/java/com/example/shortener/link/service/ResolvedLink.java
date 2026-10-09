package com.example.shortener.link.service;

import java.time.Instant;

public record ResolvedLink(int version, String originalUrl, Instant expiresAt) {
    static ResolvedLink saved(String originalUrl) {
        return saved(originalUrl, null);
    }

    static ResolvedLink saved(String originalUrl, Instant expiresAt) {
        return new ResolvedLink(1, originalUrl, expiresAt);
    }
}
