package com.example.shortener.link.service;

import com.example.shortener.link.exception.LinkExpiredException;
import com.example.shortener.link.exception.LinkNotFoundException;
import com.example.shortener.link.repository.LinkRepository;

import com.example.shortener.analytics.service.ClickRecorder;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkResolutionService {
    private final Clock clock;
    private final ClickRecorder recorder;
    private final LinkRepository repository;
    private final RedirectCache cache;
    private final LinkCacheCoordinator coordinator;
    private final TransactionTemplate transaction;

    public LinkResolutionService(LinkRepository repository, RedirectCache cache, LinkCacheCoordinator coordinator,
            PlatformTransactionManager transactionManager, Clock clock, ClickRecorder recorder) {
        this.clock = clock;
        this.recorder = recorder;
        this.repository = repository;
        this.cache = cache;
        this.coordinator = coordinator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String resolve(String shortCode) { return resolve(shortCode, false); }

    public String resolve(String shortCode, boolean countClick) {
        var group = coordinator.group(shortCode);
        var lock = group.lock.readLock();
        lock.lock();
        try {
            String generation = group.generation();
            var cached = cache.get(shortCode, group, generation);
            if (cached.isPresent()) return destination(cached.get(), countClick);
            ResolvedLink link = transaction.execute(status -> repository.findByShortCode(shortCode)
                    .map(saved -> {
                        if (saved.isExpired(clock.instant())) throw new LinkExpiredException();
                        return ResolvedLink.saved(saved.getId(), saved.getOriginalUrl(), saved.getExpiresAt());
                    })
                    .orElseThrow(LinkNotFoundException::new));
            cache.put(shortCode, link, group, generation);
            return destination(link, countClick);
        } finally {
            lock.unlock();
        }
    }

    private String destination(ResolvedLink link, boolean countClick) {
        var now = clock.instant();
        if (link.expiresAt() != null && !link.expiresAt().isAfter(now)) {
            throw new LinkExpiredException();
        }
        if (countClick) recorder.record(link.linkId(), now);
        return link.originalUrl();
    }
}
