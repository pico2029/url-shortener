package com.example.shortener.link;

import java.util.List;

public record LinkPageResponse(List<LinkListItem> items, int page, int size,
        long totalElements, int totalPages) {}
