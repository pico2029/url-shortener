package com.example.shortener.link;

import java.time.Instant;

// US09 will supply expiresAt from persistence and handle expired database results.
public record ResolvedLink(int version, String originalUrl, Instant expiresAt) {
    static ResolvedLink saved(String originalUrl) {
        return new ResolvedLink(1, originalUrl, null);
    }
}
