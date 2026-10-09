package com.example.shortener.link.service;

import com.example.shortener.link.config.RedirectCacheProperties;
import com.example.shortener.link.exception.InvalidUrlException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedirectCache {
    private static final Logger LOG = LoggerFactory.getLogger(RedirectCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final RedirectCacheProperties properties;
    private final RedirectCachePolicy policy;
    private final UrlValidator validator;
    private final AtomicLong lastWarning = new AtomicLong();

    public RedirectCache(StringRedisTemplate redis, ObjectMapper mapper, RedirectCacheProperties properties,
            RedirectCachePolicy policy, UrlValidator validator) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
        this.policy = policy;
        this.validator = validator;
    }

    Optional<ResolvedLink> get(String code, LinkCacheCoordinator.Group group, String generation) {
        if (!properties.enabled()) return Optional.empty();
        try {
            String json = redis.opsForValue().get(group.key(code, generation));
            if (json == null) return Optional.empty();
            ResolvedLink link = mapper.readValue(json, ResolvedLink.class);
            if (link == null || link.version() != 1
                    || !validator.validate(link.originalUrl()).equals(link.originalUrl())) {
                throw new InvalidUrlException();
            }
            if (policy.ttl(link).compareTo(Duration.ofMillis(1)) < 0) {
                invalidate(code, group, generation);
                return Optional.empty();
            }
            return Optional.of(link);
        } catch (DataAccessException | JsonProcessingException | InvalidUrlException exception) {
            failed(group, generation);
            return Optional.empty();
        }
    }

    void put(String code, ResolvedLink link, LinkCacheCoordinator.Group group, String generation) {
        if (!properties.enabled() || !group.isCurrent(generation)) return;
        Duration ttl = policy.ttl(link);
        if (ttl.compareTo(Duration.ofMillis(1)) < 0) return;
        try {
            redis.opsForValue().set(group.key(code, generation), mapper.writeValueAsString(link), ttl);
        } catch (DataAccessException | JsonProcessingException exception) {
            failed(group, generation);
        }
    }

    void invalidate(String code, LinkCacheCoordinator.Group group, String generation) {
        if (!properties.enabled()) return;
        try {
            redis.delete(group.key(code, generation));
        } catch (DataAccessException exception) {
            failed(group, generation);
        }
    }

    private void failed(LinkCacheCoordinator.Group group, String generation) {
        group.abandon(generation);
        long now = System.nanoTime();
        long previous = lastWarning.get();
        if ((previous == 0 || now - previous >= Duration.ofSeconds(30).toNanos())
                && lastWarning.compareAndSet(previous, now)) {
            LOG.warn("Redirect cache unavailable or invalid; using PostgreSQL and abandoning the affected namespace");
        }
    }
}
