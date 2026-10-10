package com.example.shortener.link.service;

import com.example.shortener.link.entity.Link;
import com.example.shortener.link.exception.LinkExpiredException;
import com.example.shortener.link.exception.LinkNotFoundException;
import com.example.shortener.link.repository.LinkRepository;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinkResolutionServiceTest {
    private final java.time.Clock clock = mock(java.time.Clock.class);
    private final java.time.Instant expiration = java.time.Instant.parse("2030-01-01T10:00:00Z");
    private final LinkRepository repository = mock(LinkRepository.class);
    private final RedirectCache cache = mock(RedirectCache.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final com.example.shortener.analytics.service.ClickRecorder recorder = mock(com.example.shortener.analytics.service.ClickRecorder.class);
    private final LinkResolutionService service = new LinkResolutionService(repository, cache,
            new LinkCacheCoordinator(), transactions, clock, recorder);

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(expiration.minusSeconds(60));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void checksExpirationAtTheExactBoundaryUsingTheCurrentClock() {
        when(repository.findByShortCode("Timed")).thenReturn(Optional.of(
                persisted("https://example.com/timed", "Timed", java.util.UUID.randomUUID(), expiration)));
        when(clock.instant()).thenReturn(expiration.minusNanos(1));
        assertEquals("https://example.com/timed", service.resolve("Timed"));
        verify(cache).put(eq("Timed"), eq(ResolvedLink.saved(1L, "https://example.com/timed", expiration)), any(), anyString());
        clearInvocations(cache);
        for (var now : new java.time.Instant[]{expiration, expiration.plusSeconds(1)}) {
            when(clock.instant()).thenReturn(now);
            assertThrows(LinkExpiredException.class, () -> service.resolve("Timed"));
        }
        verify(cache, never()).put(anyString(), any(), any(), anyString());
        when(repository.findByShortCode("Forever")).thenReturn(Optional.of(persisted("https://example.com/forever", "Forever")));
        assertEquals("https://example.com/forever", service.resolve("Forever"));
    }

    @Test
    void checksCachedExpirationImmediatelyBeforeReturning() {
        when(cache.get(eq("Timed"), any(), anyString()))
                .thenReturn(Optional.of(ResolvedLink.saved(1L, "https://example.com/timed", expiration)));
        when(clock.instant()).thenReturn(expiration.minusNanos(1));
        assertEquals("https://example.com/timed", service.resolve("Timed"));
        when(clock.instant()).thenReturn(expiration);
        assertThrows(LinkExpiredException.class, () -> service.resolve("Timed"));
        verifyNoInteractions(repository, transactions);
    }

    @Test
    void doesNotRedirectWhenExpirationPassesDuringCachePopulation() {
        when(repository.findByShortCode("Timed")).thenReturn(Optional.of(
                persisted("https://example.com/timed", "Timed", java.util.UUID.randomUUID(), expiration)));
        doAnswer(invocation -> {
            when(clock.instant()).thenReturn(expiration);
            return null;
        }).when(cache).put(eq("Timed"), any(), any(), anyString());
        assertThrows(LinkExpiredException.class, () -> service.resolve("Timed"));
        verify(transactions).commit(any());
    }

    @Test
    void cacheHitAvoidsDatabaseAndDoesNotRenewTtl() {
        when(cache.get(eq("Cached"), any(), anyString()))
                .thenReturn(Optional.of(ResolvedLink.saved(1L, "https://example.com/cached")));
        assertEquals("https://example.com/cached", service.resolve("Cached"));
        verifyNoInteractions(repository, transactions);
        verify(cache, never()).put(anyString(), any(), any(), anyString());
    }

    @Test
    void populatesCacheOnlyAfterDatabaseCommit() {
        when(repository.findByShortCode("Saved")).thenReturn(Optional.of(persisted("https://example.com", "Saved")));
        assertEquals("https://example.com", service.resolve("Saved"));
        var order = inOrder(transactions, cache);
        order.verify(transactions).commit(any());
        order.verify(cache).put(eq("Saved"), eq(ResolvedLink.saved(1L, "https://example.com")), any(), anyString());
    }

    @Test
    void resolvesTheSavedDestinationWithoutChangingIt() {
        String destination = "https://example.com/café/%20?q=one%2Ftwo#section";
        when(repository.findByShortCode("aB3dE7fG")).thenReturn(Optional.of(persisted(destination, "aB3dE7fG")));
        assertEquals(destination, service.resolve("aB3dE7fG"));
    }

    @Test
    void missingCodesAreLookedUpExactlyAsProvided() {
        for (String code : new String[]{"ab3de7fg", "missing", "tooLongCode", " aB3dE7fG "}) {
            when(repository.findByShortCode(code)).thenReturn(Optional.empty());
            assertThrows(LinkNotFoundException.class, () -> service.resolve(code));
            verify(repository).findByShortCode(code);
        }
        verify(cache, never()).put(anyString(), any(), any(), anyString());
    }

    @Test
    void databaseFailuresAreNotTreatedAsMissingLinks() {
        when(repository.findByShortCode("aB3dE7fG")).thenThrow(new DataAccessResourceFailureException("database"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.resolve("aB3dE7fG"));
        verify(cache, never()).put(anyString(), any(), any(), anyString());
    }
    @Test
    void tracksSuccessfulCacheAndDatabaseGetOnlyAfterValidation() {
        when(cache.get(eq("Cached"), any(), anyString()))
                .thenReturn(Optional.of(ResolvedLink.saved(42L, "https://example.com/cached")));
        service.resolve("Cached", true);
        verify(recorder).record(42L, expiration.minusSeconds(60));
        verifyNoInteractions(repository, transactions);
        when(repository.findByShortCode("Saved")).thenReturn(Optional.of(persisted("https://example.com", "Saved")));
        service.resolve("Saved", true);
        verify(recorder).record(1L, expiration.minusSeconds(60));
        service.resolve("Cached", false);
        verifyNoMoreInteractions(recorder);
    }

    @Test
    void failedOrExpiredResolutionsNeverRecordClicks() {
        when(cache.get(eq("Expired"), any(), anyString()))
                .thenReturn(Optional.of(ResolvedLink.saved(42L, "https://example.com", expiration.minusSeconds(60))));
        assertThrows(LinkExpiredException.class, () -> service.resolve("Expired", true));
        when(repository.findByShortCode("Missing")).thenReturn(Optional.empty());
        assertThrows(LinkNotFoundException.class, () -> service.resolve("Missing", true));
        when(repository.findByShortCode("Failed")).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.resolve("Failed", true));
        verifyNoInteractions(recorder);
    }

    private static Link persisted(String url, String code) {
        return persisted(url, code, null, null);
    }

    private static Link persisted(String url, String code, java.util.UUID owner, java.time.Instant expiresAt) {
        var link = new Link(url, code, owner, expiresAt);
        org.springframework.test.util.ReflectionTestUtils.setField(link, "id", 1L);
        return link;
    }
}
