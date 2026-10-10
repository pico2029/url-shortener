package com.example.shortener.analytics.service;

import com.example.shortener.analytics.dto.LinkStatistics;
import com.example.shortener.analytics.dto.LinkStatistics.DailyClicks;
import com.example.shortener.analytics.repository.ClickRepository;
import com.example.shortener.link.repository.LinkRepository;
import com.example.shortener.link.exception.LinkNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {
    private final LinkRepository links;
    private final ClickRepository clicks;
    private final Clock clock;

    public StatisticsService(LinkRepository links, ClickRepository clicks, Clock clock) {
        this.links = links;
        this.clicks = clicks;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LinkStatistics get(String shortCode, UUID ownerId) {
        var link = links.findByShortCodeAndOwnerId(shortCode, ownerId).orElseThrow(LinkNotFoundException::new);
        LocalDate to = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate from = to.minusDays(29);
        long total = clicks.total(link.getId());
        var counts = clicks.daily(link.getId(), from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        var daily = from.datesUntil(to.plusDays(1))
                .map(date -> new DailyClicks(date, counts.getOrDefault(date, 0L))).toList();
        return new LinkStatistics(shortCode, total, "UTC", from, to, daily);
    }
}
