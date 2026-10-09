package com.example.shortener.link;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinkCacheConcurrencyTest {
    private final LinkRepository repository = mock(LinkRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ConcurrentHashMap<String, String> stored = new ConcurrentHashMap<>();
    private final AtomicReference<Link> row = new AtomicReference<>(new Link("https://example.com/old", "Promo"));
    private final ThreadLocal<Boolean> inTransaction = ThreadLocal.withInitial(() -> false);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final LinkCacheCoordinator coordinator = new LinkCacheCoordinator();
    private final RedirectCacheProperties properties = new RedirectCacheProperties(true, Duration.ofMinutes(5));
    private final RedirectCache cache = new RedirectCache(redis, new ObjectMapper().findAndRegisterModules(), properties,
            new RedirectCachePolicy(Clock.systemUTC(), properties), new UrlValidator());
    private final LinkResolutionService resolution = new LinkResolutionService(repository, cache, coordinator, transactions, Clock.systemUTC());
    private final LinkService service = new LinkService(repository, new UrlValidator(), mock(ShortCodeGenerator.class),
            new LinkProperties(URI.create("http://localhost:8080")), transactions, new AliasValidator(), cache, coordinator, Clock.systemUTC());

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenAnswer(invocation -> {
            assertFalse(inTransaction.get());
            inTransaction.set(true);
            return new SimpleTransactionStatus();
        });
        doAnswer(invocation -> { inTransaction.set(false); return null; }).when(transactions).commit(any());
        doAnswer(invocation -> { inTransaction.set(false); return null; }).when(transactions).rollback(any());
        when(repository.findByShortCode("Promo")).thenAnswer(invocation -> Optional.ofNullable(row.get()));
        when(repository.deleteOwnedLink(eq("Promo"), any())).thenAnswer(invocation -> row.getAndSet(null) == null ? 0 : 1);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> {
            assertFalse(inTransaction.get(), "Redis must be outside the database transaction");
            return stored.get(invocation.getArgument(0));
        });
        doAnswer(invocation -> {
            assertFalse(inTransaction.get(), "Redis must be outside the database transaction");
            stored.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(invocation -> {
            assertFalse(inTransaction.get(), "Redis must be after commit");
            return stored.remove(invocation.getArgument(0)) != null;
        });
    }

    @Test
    void deletionWaitsForResolutionAndPreventsStaleRepopulation() throws Exception {
        var loaded = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var deleting = new CountDownLatch(1);
        when(repository.findByShortCode("Promo")).thenAnswer(invocation -> {
            Link snapshot = row.get();
            loaded.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return Optional.ofNullable(snapshot);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var redirect = executor.submit(() -> resolution.resolve("Promo"));
            assertTrue(loaded.await(5, TimeUnit.SECONDS));
            var deletion = executor.submit(() -> {
                deleting.countDown();
                service.delete("Promo", UUID.randomUUID());
            });
            assertTrue(deleting.await(5, TimeUnit.SECONDS));
            try {
                assertThrows(TimeoutException.class, () -> deletion.get(100, TimeUnit.MILLISECONDS));
                verify(repository, never()).deleteOwnedLink(anyString(), any());
            } finally {
                release.countDown();
            }
            assertEquals("https://example.com/old", redirect.get(5, TimeUnit.SECONDS));
            deletion.get(5, TimeUnit.SECONDS);
            assertTrue(stored.isEmpty());
            assertThrows(LinkNotFoundException.class, () -> resolution.resolve("Promo"));
        }
    }

    @Test
    void failedInvalidationMakesOldEntryUnreachableAndAllowsAliasReuse() {
        assertEquals("https://example.com/old", resolution.resolve("Promo"));
        var group = coordinator.group("Promo");
        String oldGeneration = group.generation();
        var other = coordinator.group("Other");
        assertNotSame(group, other);
        String otherGeneration = other.generation();
        when(redis.delete(anyString())).thenThrow(new DataAccessResourceFailureException("Redis down"));
        service.delete("Promo", UUID.randomUUID());
        assertTrue(stored.containsKey(group.key("Promo", oldGeneration)));
        assertFalse(group.isCurrent(oldGeneration));
        assertEquals(otherGeneration, other.generation());
        assertThrows(LinkNotFoundException.class, () -> resolution.resolve("Promo"));
        row.set(new Link("https://example.com/new", "Promo"));
        assertEquals("https://example.com/new", resolution.resolve("Promo"));
    }

    @Test
    void readAndWriteFailuresStillResolveFromPostgres() {
        when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("Redis down"));
        assertEquals("https://example.com/old", resolution.resolve("Promo"));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        doReturn(null).when(values).get(anyString());
        doThrow(new DataAccessResourceFailureException("Redis down")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        assertEquals("https://example.com/old", resolution.resolve("Promo"));
    }

    @Test
    void uncertainCommitAbandonsCacheAndPreserves503Behavior() {
        resolution.resolve("Promo");
        var group = coordinator.group("Promo");
        String generation = group.generation();
        doThrow(new TransactionSystemException("commit uncertain")).when(transactions).commit(any());
        assertThrows(LinkUnavailableException.class, () -> service.delete("Promo", UUID.randomUUID()));
        assertFalse(group.isCurrent(generation));
    }

    @Test
    void lateWriteAfterReadFailureCannotPopulateNewNamespace() throws Exception {
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var group = coordinator.group("Promo");
        String generation = group.generation();
        doAnswer(invocation -> {
            writing.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            stored.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> resolution.resolve("Promo"));
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("read timed out"));
            try {
                assertEquals("https://example.com/old", resolution.resolve("Promo"));
                assertFalse(group.isCurrent(generation));
            } finally {
                release.countDown();
            }
            assertEquals("https://example.com/old", first.get(5, TimeUnit.SECONDS));
            assertTrue(stored.containsKey(group.key("Promo", generation)));
            assertFalse(stored.containsKey(group.key("Promo", group.generation())));
        }
    }
}
