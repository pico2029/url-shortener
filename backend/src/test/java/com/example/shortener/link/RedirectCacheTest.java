package com.example.shortener.link;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedirectCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Instant now = Instant.parse("2026-10-09T12:00:00Z");
    private final RedirectCacheProperties properties = new RedirectCacheProperties(true, Duration.ofMinutes(5));
    private final RedirectCachePolicy policy = new RedirectCachePolicy(Clock.fixed(now, ZoneOffset.UTC), properties);
    private final LinkCacheCoordinator coordinator = new LinkCacheCoordinator();
    private final LinkCacheCoordinator.Group group = coordinator.group("Promo");
    private final RedirectCache cache = new RedirectCache(redis, mapper, properties, policy, new UrlValidator());

    @BeforeEach
    void setUp() { when(redis.opsForValue()).thenReturn(values); }

    @Test
    void writesVersionedJsonWithTtlAndDoesNotRenewOnHit() throws Exception {
        String generation = group.generation();
        String key = group.key("Promo", generation);
        var link = ResolvedLink.saved("https://example.com/café/%20?q=one%2Ftwo#fragment");
        cache.put("Promo", link, group, generation);
        verify(values).set(key, mapper.writeValueAsString(link), Duration.ofMinutes(5));
        when(values.get(key)).thenReturn(mapper.writeValueAsString(link));
        assertEquals(link, cache.get("Promo", group, generation).orElseThrow());
        verify(values, times(1)).set(anyString(), anyString(), any(Duration.class));
        assertNotEquals(key, group.key("promo", generation));
    }

    @Test
    void malformedPayloadAndReadFailureAbandonNamespace() {
        for (String json : new String[]{"{", "null", "{\"version\":2,\"originalUrl\":\"https://example.com\"}",
                "{\"version\":1,\"originalUrl\":\"javascript:alert(1)\"}"}) {
            String generation = group.generation();
            when(values.get(group.key("Promo", generation))).thenReturn(json);
            assertTrue(cache.get("Promo", group, generation).isEmpty());
            assertFalse(group.isCurrent(generation));
            cache.put("Promo", ResolvedLink.saved("https://example.com"), group, generation);
        }
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        String generation = group.generation();
        when(values.get(group.key("Promo", generation))).thenThrow(new DataAccessResourceFailureException("secret"));
        assertTrue(cache.get("Promo", group, generation).isEmpty());
        assertFalse(group.isCurrent(generation));
    }

    @Test
    void writeAndInvalidationFailuresDoNotEscapeAndAbandonNamespace() {
        String generation = group.generation();
        doThrow(new DataAccessResourceFailureException("secret")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        assertDoesNotThrow(() -> cache.put("Promo", ResolvedLink.saved("https://example.com"), group, generation));
        assertFalse(group.isCurrent(generation));
        String next = group.generation();
        when(redis.delete(group.key("Promo", next))).thenThrow(new DataAccessResourceFailureException("secret"));
        assertDoesNotThrow(() -> cache.invalidate("Promo", group, next));
        assertFalse(group.isCurrent(next));
    }

    @Test
    void expiryPolicyCapsTtlAndRejectsExpiredOrSubmillisecondEntries() throws Exception {
        String generation = group.generation();
        var future = new ResolvedLink(1, "https://example.com", now.plusSeconds(12));
        cache.put("Promo", future, group, generation);
        verify(values).set(group.key("Promo", generation), mapper.writeValueAsString(future), Duration.ofSeconds(12));
        assertEquals(Duration.ofMinutes(5), policy.ttl(new ResolvedLink(1, future.originalUrl(), now.plusSeconds(900))));
        for (Instant expiry : new Instant[]{Instant.MIN, now.minusSeconds(1), now, now.plusNanos(999999)}) {
            var expired = new ResolvedLink(1, future.originalUrl(), expiry);
            cache.put("Promo", expired, group, generation);
            when(values.get(group.key("Promo", generation))).thenReturn(mapper.writeValueAsString(expired));
            assertTrue(cache.get("Promo", group, generation).isEmpty());
        }
        verify(values, times(1)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void disabledCacheNeverConnectsAndPropertiesRejectNonpositiveTtl() {
        var disabled = new RedirectCache(redis, mapper, new RedirectCacheProperties(false, Duration.ofMinutes(5)),
                policy, new UrlValidator());
        clearInvocations(redis);
        assertTrue(disabled.get("Promo", group, group.generation()).isEmpty());
        disabled.put("Promo", ResolvedLink.saved("https://example.com"), group, group.generation());
        disabled.invalidate("Promo", group, group.generation());
        verifyNoInteractions(redis);
        assertThrows(IllegalArgumentException.class, () -> new RedirectCacheProperties(true, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RedirectCacheProperties(true, Duration.ofMillis(-1)));
    }

    @Test
    void processNamespacesAreIndependentAndLateFailuresCannotRotateNewGeneration() {
        var other = new LinkCacheCoordinator().group("Promo");
        assertNotEquals(group.key("Promo", group.generation()), other.key("Promo", other.generation()));
        String generation = group.generation();
        group.abandon(generation);
        String next = group.generation();
        group.abandon(generation);
        assertEquals(next, group.generation());
    }
}
