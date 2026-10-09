package com.example.shortener.link;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkResolutionService {
    private final LinkRepository repository;
    private final RedirectCache cache;
    private final LinkCacheCoordinator coordinator;
    private final TransactionTemplate transaction;

    public LinkResolutionService(LinkRepository repository, RedirectCache cache, LinkCacheCoordinator coordinator,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.cache = cache;
        this.coordinator = coordinator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public String resolve(String shortCode) {
        var group = coordinator.group(shortCode);
        var lock = group.lock.readLock();
        lock.lock();
        try {
            String generation = group.generation();
            var cached = cache.get(shortCode, group, generation);
            if (cached.isPresent()) return cached.get().originalUrl();
            ResolvedLink link = transaction.execute(status -> repository.findByShortCode(shortCode)
                    .map(saved -> ResolvedLink.saved(saved.getOriginalUrl()))
                    .orElseThrow(LinkNotFoundException::new));
            cache.put(shortCode, link, group, generation);
            return link.originalUrl();
        } finally {
            lock.unlock();
        }
    }
}
