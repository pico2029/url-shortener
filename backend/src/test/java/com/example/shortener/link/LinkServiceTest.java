package com.example.shortener.link;

import java.net.URI;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LinkServiceTest {
    private final LinkRepository repository = mock(LinkRepository.class);
    private final ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private LinkService service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(generator.generate()).thenReturn("aB3dE7fG", "bB3dE7fG");
        service = new LinkService(repository, new UrlValidator(), generator,
                new LinkProperties(URI.create("http://localhost:8080")), transactions, new AliasValidator());
    }

    @Test
    void commitsBeforeReturningAndCreatesNewCodes() {
        var first = service.create(" https://example.com ");
        var second = service.create("https://example.com");
        assertEquals("http://localhost:8080/r/aB3dE7fG", first.shortUrl());
        assertEquals("https://example.com", first.originalUrl());
        assertNotEquals(first.shortCode(), second.shortCode());
        verify(transactions, times(2)).commit(any());
        var definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definitions.capture());
        assertTrue(definitions.getAllValues().stream().allMatch(d ->
                d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void invalidInputNeverStartsTransaction() {
        assertThrows(InvalidUrlException.class, () -> service.create("invalid"));
        verifyNoInteractions(repository, transactions, generator);
    }

    @Test
    void retriesOnlyCodeCollisionsInSeparateTransactions() {
        when(repository.saveAndFlush(any())).thenThrow(collision()).thenAnswer(i -> i.getArgument(0));
        assertEquals("bB3dE7fG", service.create("https://example.com").shortCode());
        verify(transactions).rollback(any());
        verify(transactions).commit(any());
        verify(transactions, times(2)).getTransaction(any());
    }

    @Test
    void stopsAfterFiveCollisions() {
        when(repository.saveAndFlush(any())).thenThrow(collision());
        assertThrows(LinkUnavailableException.class, () -> service.create("https://example.com"));
        verify(repository, times(5)).saveAndFlush(any());
        verify(transactions, times(5)).rollback(any());
    }

    @Test
    void doesNotRetryOtherDatabaseFailuresOrReportSuccessOnCommitFailure() {
        when(repository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("database"));
        assertThrows(LinkUnavailableException.class, () -> service.create("https://example.com"));
        verify(repository).saveAndFlush(any());
        reset(repository);
        doThrow(new org.springframework.transaction.TransactionSystemException("commit")).when(transactions).commit(any());
        assertThrows(LinkUnavailableException.class, () -> service.create("https://example.com"));
    }

    private DataIntegrityViolationException collision() {
        return new DataIntegrityViolationException("collision", new SQLException("uk_links_short_code", "23505"));
    }

    @Test
    void savesNormalizedAliasWithoutGeneratingCode() {
        assertEquals("Promo-2026", service.create("https://example.com", "  Promo-2026  ").shortCode());
        verifyNoInteractions(generator);
        verify(transactions).commit(any());
        var saved = ArgumentCaptor.forClass(Link.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals("Promo-2026", saved.getValue().getShortCode());
    }

    @Test
    void explicitCollisionIsNotRetriedOrReplaced() {
        when(repository.saveAndFlush(any())).thenThrow(collision());
        assertThrows(AliasAlreadyTakenException.class, () -> service.create("https://example.com", "Promo"));
        verify(repository).saveAndFlush(any());
        verify(transactions).rollback(any());
        verifyNoInteractions(generator);
    }

    @Test
    void invalidAliasNeverStartsTransaction() {
        assertThrows(InvalidAliasException.class, () -> service.create("https://example.com", "bad alias"));
        verifyNoInteractions(repository, transactions, generator);
    }

    @Test
    void blankAliasGeneratesCode() {
        assertEquals("aB3dE7fG", service.create("https://example.com", " \t\u2003 ").shortCode());
        verify(generator).generate();
    }

    @Test
    void aliasDoesNotTurnOtherConstraintsOrCommitFailureIntoConflict() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("other",
                new SQLException("another_constraint", "23505")));
        assertThrows(LinkUnavailableException.class, () -> service.create("https://example.com", "Promo"));
        verify(repository).saveAndFlush(any());
        reset(repository);
        doThrow(new org.springframework.transaction.TransactionSystemException("commit")).when(transactions).commit(any());
        assertThrows(LinkUnavailableException.class, () -> service.create("https://example.com", "Promo"));
        verifyNoInteractions(generator);
    }
}
