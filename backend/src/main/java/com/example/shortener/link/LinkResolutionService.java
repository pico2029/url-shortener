package com.example.shortener.link;

import org.springframework.stereotype.Service;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkResolutionService {
    private final LinkRepository repository;
    private final Clock clock;

    public LinkResolutionService(LinkRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public String resolve(String shortCode) {
        Link link = repository.findByShortCode(shortCode).orElseThrow(LinkNotFoundException::new);
        if (link.isExpired(clock.instant())) throw new LinkExpiredException();
        return link.getOriginalUrl();
    }
}
