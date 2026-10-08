package com.example.shortener.link;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LinkPostgresIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired private LinkService service;
    @Autowired private com.example.shortener.user.UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @MockitoSpyBean private ShortCodeGenerator generator;

    @Test
    void migrationPersistenceAndRealUniqueConstraintRetry() {
        doReturn("AAAAAAAA", "AAAAAAAA", "BBBBBBBB").when(generator).generate();
        var first = service.create("https://example.com/path?q=one#fragment");
        var owner = users.saveAndFlush(new com.example.shortener.user.User("retry-owner@example.com", "unused"));
        var second = service.create("https://example.com/path?q=one#fragment", owner.getId());
        assertEquals(owner.getId(), jdbc.queryForObject("SELECT owner_id FROM links WHERE short_code = 'BBBBBBBB'", java.util.UUID.class));
        assertNull(jdbc.queryForObject("SELECT owner_id FROM links WHERE short_code = 'AAAAAAAA'", java.util.UUID.class));
        assertNotEquals(first.shortCode(), second.shortCode());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM links WHERE short_code IN ('AAAAAAAA','BBBBBBBB')", Integer.class));
        assertEquals(first.originalUrl(), jdbc.queryForObject("SELECT original_url FROM links WHERE short_code = 'AAAAAAAA'", String.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO links(original_url, short_code, created_at) VALUES (?, ?, now())", "https://example.com", "AAAAAAAA"));
    }

    @Test
    void concurrentSubmissionsCreateDistinctPersistedLinks() throws Exception {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM links", Integer.class);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = IntStream.range(0, 12)
                    .mapToObj(i -> executor.submit(() -> service.create("http://localhost:3000/concurrent")))
                    .toList();
            var codes = new java.util.HashSet<String>();
            for (var future : futures) codes.add(future.get().shortCode());
            assertEquals(12, codes.size());
        }
        assertEquals(before + 12, jdbc.queryForObject("SELECT count(*) FROM links", Integer.class));
    }

    @Test
    void invalidAndMissingUrlsNeverInsertRows() throws Exception {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM links", Integer.class);
        for (String body : new String[]{"{}", "{\"originalUrl\":null}", "{\"originalUrl\":\"ftp://example.com\"}", "{"}) {
            mvc.perform(post("/api/v1/links").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM links", Integer.class));
    }
}
