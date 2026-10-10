package com.example.shortener.analytics.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClickRepository {
    private final JdbcTemplate jdbc;

    public ClickRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(long linkId, Instant occurredAt) {
        jdbc.update("INSERT INTO click_events (link_id, occurred_at) VALUES (?, ?)", linkId, Timestamp.from(occurredAt));
    }

    public long total(long linkId) {
        return jdbc.queryForObject("SELECT count(*) FROM click_events WHERE link_id = ?", Long.class, linkId);
    }

    public Map<LocalDate, Long> daily(long linkId, Instant from, Instant until) {
        return jdbc.query("""
                SELECT (occurred_at AT TIME ZONE 'UTC')::date AS day, count(*) AS clicks
                FROM click_events WHERE link_id = ? AND occurred_at >= ? AND occurred_at < ?
                GROUP BY day
                """, (row, index) -> Map.entry(row.getObject("day", LocalDate.class), row.getLong("clicks")),
                linkId, Timestamp.from(from), Timestamp.from(until))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
