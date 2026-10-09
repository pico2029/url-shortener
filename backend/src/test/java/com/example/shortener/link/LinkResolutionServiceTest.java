package com.example.shortener.link;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinkResolutionServiceTest {
    private final LinkRepository repository = mock(LinkRepository.class);
    private final LinkResolutionService service = new LinkResolutionService(repository, java.time.Clock.systemUTC());

    @Test
    void checksExpirationAtTheExactBoundaryUsingTheCurrentClock() {
        var clock = mock(java.time.Clock.class);
        var timedService = new LinkResolutionService(repository, clock);
        var expiration = java.time.Instant.parse("2030-01-01T10:00:00Z");
        when(repository.findByShortCode("Timed")).thenReturn(Optional.of(
                new Link("https://example.com/timed", "Timed", java.util.UUID.randomUUID(), expiration)));
        when(repository.findByShortCode("Forever")).thenReturn(Optional.of(new Link("https://example.com/forever", "Forever")));
        when(clock.instant()).thenReturn(expiration.minusNanos(1));
        assertEquals("https://example.com/timed", timedService.resolve("Timed"));
        when(clock.instant()).thenReturn(expiration);
        assertThrows(LinkExpiredException.class, () -> timedService.resolve("Timed"));
        when(clock.instant()).thenReturn(expiration.plusSeconds(1));
        assertThrows(LinkExpiredException.class, () -> timedService.resolve("Timed"));
        assertEquals("https://example.com/forever", timedService.resolve("Forever"));
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
    }

    @Test
    void databaseFailuresAreNotTreatedAsMissingLinks() {
        when(repository.findByShortCode("aB3dE7fG")).thenThrow(new DataAccessResourceFailureException("database"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.resolve("aB3dE7fG"));
    }
}
