package com.example.shortener.link.service;

import com.example.shortener.link.entity.Link;
import com.example.shortener.link.repository.LinkRepository;

import com.example.shortener.user.entity.User;
import com.example.shortener.user.repository.UserRepository;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// These containers are reached through Docker Desktop's host gateway rather than
// the application's internal network. Leave scheduling margin for behavioral tests.
@SpringBootTest(properties = {"shortener.redirect-cache.ttl=30s",
        "spring.data.redis.connect-timeout=1s", "spring.data.redis.timeout=1s"})
@AutoConfigureMockMvc
@Testcontainers
class RedirectCacheIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.6-alpine")
            .withExposedPorts(6379).withCommand("redis-server", "--save", "", "--appendonly", "no");

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private ApplicationContext context;
    @MockitoBean private Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-01T10:00:00Z"));
    }

    @Autowired private MockMvc mvc;
    @Autowired private LinkService links;
    @Autowired private LinkResolutionService resolution;
    @Autowired private LinkCacheCoordinator coordinator;
    @Autowired private RedirectCache cache;
    @Autowired private UserRepository users;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoSpyBean private LinkRepository repository;

    @Test
    void persistedExpirationCapsCacheTtlAndReturnsGoneWhileRedisStillHasTheEntry() throws Exception {
        assertEquals(1, context.getBeansOfType(Clock.class).size());
        var expiration = clock.instant().plusSeconds(20);
        var owner = users.saveAndFlush(new User("cache-expiration@example.com", "unused"));
        var created = links.create("https://example.com/timed", "CacheTimed", owner.getId(), expiration);
        clearInvocations(repository);
        mvc.perform(get("/r/CacheTimed")).andExpect(status().isFound());
        var group = coordinator.group(created.shortCode());
        String key = group.key(created.shortCode(), group.generation());
        long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 20000);
        assertEquals(expiration, cache.get(created.shortCode(), group, group.generation()).orElseThrow().expiresAt());
        mvc.perform(head("/r/CacheTimed")).andExpect(status().isFound()).andExpect(content().string(""));
        verify(repository, times(1)).findByShortCode(created.shortCode());
        // Keep the key alive to prove expiration is checked independently of Redis eviction.
        redis.expire(key, Duration.ofMinutes(1));
        when(clock.instant()).thenReturn(expiration);
        assertTrue(Boolean.TRUE.equals(redis.hasKey(key)));
        mvc.perform(get("/r/CacheTimed")).andExpect(status().isGone())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Link expired")));
        mvc.perform(head("/r/CacheTimed")).andExpect(status().isGone())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(key)));
        verify(repository, times(3)).findByShortCode(created.shortCode());
    }

    @Test
    void hitAvoidsPostgresAndDoesNotRenewRedisTtl() throws Exception {
        var created = links.create("https://example.com/café/%20?q=one%2Ftwo#fragment", "CacheHit");
        clearInvocations(repository);
        mvc.perform(get("/r/" + created.shortCode()).queryParam("extra", "ignored"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/caf%C3%A9/%20?q=one%2Ftwo#fragment"))
                .andExpect(header().string("Cache-Control", "no-store"));
        var group = coordinator.group(created.shortCode());
        String key = group.key(created.shortCode(), group.generation());
        long initial = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(initial > 0 && initial <= Duration.ofSeconds(30).toMillis());
        // Shorten the actual server TTL; an accidental renewal on hit would restore 30 seconds.
        redis.expire(key, Duration.ofSeconds(10));
        mvc.perform(head("/r/" + created.shortCode())).andExpect(status().isFound())
                .andExpect(content().string(""));
        verify(repository, times(1)).findByShortCode(created.shortCode());
        assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) <= 10000);
        mvc.perform(get("/r/cachehit")).andExpect(status().isNotFound());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(group.key("cachehit", group.generation()))));
        redis.expire(key, Duration.ofMillis(1));
        await().atMost(Duration.ofSeconds(2)).until(() -> Boolean.FALSE.equals(redis.hasKey(key)));
        mvc.perform(get("/r/" + created.shortCode())).andExpect(status().isFound());
        verify(repository, times(2)).findByShortCode(created.shortCode());
    }

    @Test
    void cachedRedirectSurvivesDatabaseFailureButMissReturns503() throws Exception {
        var created = links.create("https://example.com/database-outage", "CacheDatabase");
        resolution.resolve(created.shortCode());
        doThrow(new DataAccessResourceFailureException("internal details"))
                .when(repository).findByShortCode(created.shortCode());
        mvc.perform(get("/r/" + created.shortCode())).andExpect(status().isFound());
        var group = coordinator.group(created.shortCode());
        redis.delete(group.key(created.shortCode(), group.generation()));
        mvc.perform(get("/r/" + created.shortCode())).andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentType("text/html;charset=UTF-8"));
    }

    @Test
    void deletionInvalidatesRealCacheAndAliasReuseHasNewDestination() throws Exception {
        var owner = users.saveAndFlush(new User("cache-owner@example.com", "unused"));
        var created = links.create("https://example.com/old", "CacheDelete", owner.getId());
        resolution.resolve(created.shortCode());
        var group = coordinator.group(created.shortCode());
        String key = group.key(created.shortCode(), group.generation());
        assertTrue(Boolean.TRUE.equals(redis.hasKey(key)));
        links.delete(created.shortCode(), owner.getId());
        assertFalse(Boolean.TRUE.equals(redis.hasKey(key)));
        mvc.perform(get("/r/" + created.shortCode())).andExpect(status().isNotFound());
        links.create("https://example.com/new", created.shortCode(), owner.getId());
        mvc.perform(get("/r/" + created.shortCode())).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/new"));
    }

    @Test
    void redisOutageFallsBackAndRecoveryNeverRevivesDeletedCache() throws Exception {
        var owner = users.saveAndFlush(new User("cache-outage@example.com", "unused"));
        var deleted = links.create("https://example.com/deleted", "CacheOutage", owner.getId());
        var kept = links.create("https://example.com/kept", "CacheKept");
        resolution.resolve(deleted.shortCode());
        var group = coordinator.group(deleted.shortCode());
        String oldKey = group.key(deleted.shortCode(), group.generation());
        String oldPayload = redis.opsForValue().get(oldKey);
        assertNotNull(oldPayload);
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
            mvc.perform(get("/r/" + kept.shortCode())).andExpect(status().isFound());
            links.delete(deleted.shortCode(), owner.getId());
            mvc.perform(get("/r/" + deleted.shortCode())).andExpect(status().isNotFound());
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        assertEquals("PONG", REDIS.execInContainer("redis-cli", "ping").getStdout().strip());
        // A timed-out DEL may execute when Redis resumes. Restore the pre-outage
        // snapshot to also exercise the worst case where invalidation was lost.
        redis.opsForValue().set(oldKey, oldPayload, Duration.ofSeconds(30));
        assertTrue(Boolean.TRUE.equals(redis.hasKey(oldKey)), "A stale entry exists after recovery");
        mvc.perform(get("/r/" + deleted.shortCode())).andExpect(status().isNotFound());
        mvc.perform(get("/r/" + kept.shortCode())).andExpect(status().isFound());
    }

    @Test
    void newProcessNamespaceIgnoresOldCacheAndMalformedPayloadFallsBack() {
        var created = links.create("https://example.com/process", "CacheProcess");
        resolution.resolve(created.shortCode());
        var group = coordinator.group(created.shortCode());
        String key = group.key(created.shortCode(), group.generation());
        assertTrue(Boolean.TRUE.equals(redis.hasKey(key)));
        var restarted = new LinkResolutionService(repository, cache, new LinkCacheCoordinator(), transactions, clock, context.getBean(com.example.shortener.analytics.service.ClickRecorder.class));
        clearInvocations(repository);
        assertEquals(created.originalUrl(), restarted.resolve(created.shortCode()));
        verify(repository).findByShortCode(created.shortCode());
        assertTrue(Boolean.TRUE.equals(redis.hasKey(key)));
        redis.opsForValue().set(key, "{broken", Duration.ofSeconds(30));
        clearInvocations(repository);
        assertEquals(created.originalUrl(), resolution.resolve(created.shortCode()));
        verify(repository).findByShortCode(created.shortCode());
    }
}
