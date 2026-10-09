package com.example.shortener.link;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinkResolutionServiceTest {
    private final LinkRepository repository = mock(LinkRepository.class);
    private final RedirectCache cache = mock(RedirectCache.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final LinkResolutionService service = new LinkResolutionService(repository, cache,
            new LinkCacheCoordinator(), transactions);

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void cacheHitAvoidsDatabaseAndDoesNotRenewTtl() {
        when(cache.get(eq("Cached"), any(), anyString()))
                .thenReturn(Optional.of(ResolvedLink.saved("https://example.com/cached")));
        assertEquals("https://example.com/cached", service.resolve("Cached"));
        verifyNoInteractions(repository, transactions);
        verify(cache, never()).put(anyString(), any(), any(), anyString());
    }

    @Test
    void populatesCacheOnlyAfterDatabaseCommit() {
        when(repository.findByShortCode("Saved")).thenReturn(Optional.of(new Link("https://example.com", "Saved")));
        assertEquals("https://example.com", service.resolve("Saved"));
        var order = inOrder(transactions, cache);
        order.verify(transactions).commit(any());
        order.verify(cache).put(eq("Saved"), eq(ResolvedLink.saved("https://example.com")), any(), anyString());
    }

    @Test
    void resolvesTheSavedDestinationWithoutChangingIt() {
        String destination = "https://example.com/café/%20?q=one%2Ftwo#section";
        when(repository.findByShortCode("aB3dE7fG")).thenReturn(Optional.of(new Link(destination, "aB3dE7fG")));
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
}
