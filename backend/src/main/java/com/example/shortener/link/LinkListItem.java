package com.example.shortener.link;

import java.time.Instant;

public record LinkListItem(String originalUrl, String shortCode, String shortUrl,
        Instant createdAt, String status) {}
