package com.example.shortener.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortener.analytics")
public record AnalyticsProperties(int queueCapacity) {
    public AnalyticsProperties {
        if (queueCapacity < 1) throw new IllegalArgumentException("shortener.analytics.queue-capacity must be positive");
    }
}
