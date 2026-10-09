package com.example.shortener.link.service;

import com.example.shortener.link.config.LinkProperties;
import com.example.shortener.link.dto.CreateLinkResponse;
import com.example.shortener.link.dto.LinkListItem;
import com.example.shortener.link.dto.LinkPageResponse;
import com.example.shortener.link.entity.Link;
import com.example.shortener.link.exception.AliasAlreadyTakenException;
import com.example.shortener.link.exception.ExpirationAuthenticationException;
import com.example.shortener.link.exception.InvalidExpirationException;
import com.example.shortener.link.exception.InvalidPageException;
import com.example.shortener.link.exception.LinkNotFoundException;
import com.example.shortener.link.exception.LinkUnavailableException;
import com.example.shortener.link.repository.LinkRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import com.example.shortener.auth.exception.InvalidCredentialsException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkService {
    private final Clock clock;
    private final LinkRepository repository;
    private final UrlValidator validator;
    private final AliasValidator aliases;
    private final ShortCodeGenerator generator;
    private final LinkProperties properties;
    private final TransactionTemplate transaction;
    private final RedirectCache cache;
    private final LinkCacheCoordinator coordinator;

    public LinkService(LinkRepository repository, UrlValidator validator, ShortCodeGenerator generator,
            LinkProperties properties, PlatformTransactionManager transactionManager, AliasValidator aliases,
            RedirectCache cache, LinkCacheCoordinator coordinator, Clock clock) {
        this.clock = clock;
        this.repository = repository;
        this.validator = validator;
        this.aliases = aliases;
        this.generator = generator;
        this.properties = properties;
        this.cache = cache;
        this.coordinator = coordinator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void delete(String shortCode, UUID ownerId) {
        if (ownerId == null) throw new InvalidCredentialsException();
        var group = coordinator.group(shortCode);
        var lock = group.lock.writeLock();
        lock.lock();
        try {
            transaction.executeWithoutResult(status -> {
                if (repository.deleteOwnedLink(shortCode, ownerId) == 0) throw new LinkNotFoundException();
            });
            cache.invalidate(shortCode, group, group.generation());
        } catch (DataAccessException | TransactionException exception) {
            group.abandon(group.generation());
            throw new LinkUnavailableException(exception);
        } finally {
            lock.unlock();
        }
    }

    public CreateLinkResponse create(String originalUrl) {
        return create(originalUrl, null);
    }

    @Transactional(readOnly = true)
    public LinkPageResponse list(UUID ownerId, int page, int size) {
        if (ownerId == null) throw new InvalidCredentialsException();
        if (page < 0 || size < 1 || size > 100) throw new InvalidPageException();
        try {
            var result = repository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId, PageRequest.of(page, size));
            Instant now = clock.instant();
            var items = result.getContent().stream().map(link -> new LinkListItem(link.getOriginalUrl(),
                    link.getShortCode(), properties.shortUrl(link.getShortCode()), link.getCreatedAt(), link.isExpired(now) ? "EXPIRED" : "SAVED", link.getExpiresAt())).toList();
            return new LinkPageResponse(items, page, size, result.getTotalElements(), result.getTotalPages());
        } catch (DataAccessException exception) {
            throw new LinkUnavailableException(exception);
        }
    }

    public CreateLinkResponse create(String originalUrl, String requestedAlias) {
        return create(originalUrl, requestedAlias, null);
    }

    public CreateLinkResponse create(String originalUrl, String requestedAlias, UUID ownerId) {
        return create(originalUrl, requestedAlias, ownerId, null);
    }

    public CreateLinkResponse create(String originalUrl, String requestedAlias, UUID ownerId, Instant expiresAt) {
        if (expiresAt != null && ownerId == null) throw new ExpirationAuthenticationException();
        Instant expiration = expiresAt == null ? null : expiresAt.truncatedTo(ChronoUnit.MICROS);
        String alias = aliases.validate(requestedAlias);
        String url = validator.validate(originalUrl);
        int attempts = alias == null ? 5 : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            String code = alias == null ? generator.generate() : alias;
            try {
                transaction.executeWithoutResult(status -> {
                    if (expiration != null && !expiration.isAfter(clock.instant())) throw new InvalidExpirationException();
                    repository.saveAndFlush(new Link(url, code, ownerId, expiration));
                });
                return new CreateLinkResponse(url, code, properties.shortUrl(code), expiration);
            } catch (DataAccessException | TransactionException exception) {
                if (alias != null && isCodeCollision(exception)) throw new AliasAlreadyTakenException();
                if (!isCodeCollision(exception) || attempt == 4) {
                    throw new LinkUnavailableException(exception);
                }
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private boolean isCodeCollision(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains("uk_links_short_code")) {
                return true;
            }
        }
        return false;
    }
}
