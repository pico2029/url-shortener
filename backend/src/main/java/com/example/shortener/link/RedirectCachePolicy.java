package com.example.shortener.link;

import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RedirectCachePolicy {
    private final Clock clock;
    private final RedirectCacheProperties properties;

    public RedirectCachePolicy(Clock clock, RedirectCacheProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    Duration ttl(ResolvedLink link) {
        if (link.expiresAt() == null) return properties.ttl();
        Duration remaining = Duration.between(clock.instant(), link.expiresAt());
        return remaining.compareTo(properties.ttl()) < 0 ? remaining : properties.ttl();
    }
}
