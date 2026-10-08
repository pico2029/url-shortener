package com.example.shortener.link;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkResolutionService {
    private final LinkRepository repository;

    public LinkResolutionService(LinkRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String resolve(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(Link::getOriginalUrl)
                .orElseThrow(LinkNotFoundException::new);
    }
}
