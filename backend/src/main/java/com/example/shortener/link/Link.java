package com.example.shortener.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "links")
public class Link {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "original_url", nullable = false, length = 8192)
    private String originalUrl;
    @Column(name = "short_code", nullable = false, length = 32)
    private String shortCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "owner_id")
    private UUID ownerId;

    protected Link() {
    }

    Link(String originalUrl, String shortCode) {
        this(originalUrl, shortCode, null);
    }

    Link(String originalUrl, String shortCode, UUID ownerId) {
        this(originalUrl, shortCode, ownerId, null);
    }

    Link(String originalUrl, String shortCode, UUID ownerId, Instant expiresAt) {
        this.expiresAt = expiresAt;
        this.ownerId = ownerId;
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.createdAt = Instant.now();
    }

    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }

    public UUID getOwnerId() { return ownerId; }
    public Instant getCreatedAt() { return createdAt; }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }
}
