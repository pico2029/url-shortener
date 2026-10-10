package com.example.shortener.link.service;

import java.time.Instant;

public record ResolvedLink(int version, long linkId, String originalUrl, Instant expiresAt) {
    static ResolvedLink saved(long linkId, String originalUrl) {
        return saved(linkId, originalUrl, null);
    }

    static ResolvedLink saved(long linkId, String originalUrl, Instant expiresAt) {
        return new ResolvedLink(2, linkId, originalUrl, expiresAt);
    }
}
