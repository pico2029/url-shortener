package com.example.shortener.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record LinkStatistics(String shortCode, long totalClicks, String timezone,
        LocalDate from, LocalDate to, List<DailyClicks> daily) {
    public record DailyClicks(LocalDate date, long clicks) {}
}
