package com.example.shortener.link;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortener.redirect-cache")
public record RedirectCacheProperties(boolean enabled, Duration ttl) {
    public RedirectCacheProperties {
        if (ttl == null || ttl.isNegative() || ttl.toMillis() < 1) {
            throw new IllegalArgumentException("shortener.redirect-cache.ttl must be at least 1ms");
        }
    }
}
