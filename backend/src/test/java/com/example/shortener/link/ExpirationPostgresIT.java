package com.example.shortener.link;

import com.example.shortener.security.TokenService;
import com.example.shortener.user.User;
import com.example.shortener.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class ExpirationPostgresIT {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokens;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @MockitoBean private Clock clock;
    private String token;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.update("DELETE FROM links");
        users.deleteAll();
        token = tokens.issue(users.saveAndFlush(new User("expire@example.com", "unused")).getId()).accessToken();
    }

    @Test
    void preservesLegacyLinksAndStoresOptionalExpirationWithAnAlias() throws Exception {
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).target("4").load().migrate();
        jdbc.update("INSERT INTO links (original_url, short_code, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                "https://example.com/legacy", "Legacy01");
        Flyway.configure().dataSource(dataSource).load().migrate();
        assertNull(jdbc.queryForObject("SELECT expires_at FROM links WHERE short_code = 'Legacy01'", Object.class));
        mvc.perform(get("/r/Legacy01")).andExpect(status().isFound());
        token = tokens.issue(users.saveAndFlush(new User("expire@example.com", "unused")).getId()).accessToken();
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(mapper.writeValueAsBytes(Map.of("originalUrl", "https://example.com/new", "alias", "Timed-Alias",
                        "expiresAt", "2030-01-01T12:01:00.123456789+02:00"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").value("2030-01-01T10:01:00.123456Z"));
        assertEquals(Instant.parse("2030-01-01T10:01:00.123456Z"),
                jdbc.queryForObject("SELECT expires_at FROM links WHERE short_code = 'Timed-Alias'", java.sql.Timestamp.class).toInstant());
        mvc.perform(post("/api/v1/links").contentType("application/json").content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rejectsInvalidExpirationAndAnonymousExpirationWithoutInsertion() throws Exception {
        for (String instant : new String[]{"2029-12-31T10:00:00Z", NOW.toString(), "2030-01-01T10:00:00.000000001Z"}) {
            mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token).contentType("application/json")
                    .content(mapper.writeValueAsBytes(Map.of("originalUrl", "https://example.com", "expiresAt", instant))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EXPIRATION"))
                    .andExpect(jsonPath("$.field").value("expiresAt"));
        }
        for (String value : new String[]{"123", "true", "[]", "\"\"", "\"2030-01-02T10:00:00\"", "\"invalid\""}) {
            mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token).contentType("application/json")
                    .content("{\"originalUrl\":\"https://example.com\",\"expiresAt\":" + value + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(post("/api/v1/links").contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com\",\"expiresAt\":\"2030-01-02T10:00:00Z\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM links", Long.class));
    }

    @Test
    void expiredGetHeadAndPersonalListingRespectTheBoundaryAndOwnership() throws Exception {
        Instant expiration = NOW.plusSeconds(60);
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(mapper.writeValueAsBytes(Map.of("originalUrl", "https://example.com/timed", "alias", "Timed",
                        "expiresAt", expiration.toString())))).andExpect(status().isCreated());
        mvc.perform(get("/r/Timed")).andExpect(status().isFound());
        when(clock.instant()).thenReturn(expiration);
        mvc.perform(get("/r/Timed")).andExpect(status().isGone()).andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Link expired")));
        mvc.perform(head("/r/Timed")).andExpect(status().isGone()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Location")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/links").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items[0].expiresAt").value(expiration.toString()))
                .andExpect(jsonPath("$.items[0].status").value("EXPIRED"));
        String other = tokens.issue(users.saveAndFlush(new User("other@example.com", "unused")).getId()).accessToken();
        mvc.perform(get("/api/v1/links").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com\",\"alias\":\"Timed\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/links/Timed").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/r/Timed")).andExpect(status().isNotFound());
    }
}
