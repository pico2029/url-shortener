package com.example.shortener.link;

import com.example.shortener.link.exception.AliasAlreadyTakenException;
import com.example.shortener.link.service.LinkService;
import com.example.shortener.link.service.ShortCodeGenerator;
import com.example.shortener.user.entity.User;
import com.example.shortener.user.repository.UserRepository;

import com.example.shortener.security.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "shortener.redirect-cache.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class AliasPostgresIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired private LinkService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtEncoder encoder;
    @Autowired private com.example.shortener.user.repository.UserRepository users;
    @MockitoSpyBean private ShortCodeGenerator generator;

    @Test
    void authenticatedLongAliasIsPersistedAndOpensPublicly() throws Exception {
        String alias = "Campaign_" + "A".repeat(23);
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token())
                .contentType("application/json").content(body(" " + alias + " ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.shortCode").value(alias))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/r/" + alias));
        mvc.perform(get("/r/" + alias)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/alias"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(head("/r/" + alias)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/alias"))
                .andExpect(content().string(""));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM links WHERE short_code = ?", Integer.class, alias));
        assertNotNull(jdbc.queryForObject("SELECT owner_id FROM links WHERE short_code = ?", UUID.class, alias));
    }

    @Test
    void explicitAliasesAndGeneratedCodesShareCaseSensitiveUniqueness() throws Exception {
        service.create("https://example.com", "Promo");
        service.create("https://example.com", "promo");
        assertThrows(AliasAlreadyTakenException.class, () -> service.create("https://example.com", "Promo"));
        doReturn("Alias123", "Fresh123").when(generator).generate();
        service.create("https://example.com", "Alias123");
        assertEquals("Fresh123", service.create("https://example.com").shortCode());
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token())
                .contentType("application/json").content(body("Fresh123")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALIAS_ALREADY_TAKEN"))
                .andExpect(jsonPath("$.field").value("alias"));
        assertEquals(4, jdbc.queryForObject(
                "SELECT count(*) FROM links WHERE short_code IN ('Promo','promo','Alias123','Fresh123')", Integer.class));
    }

    @Test
    void concurrentHttpRequestsProduceOneCreatedAndOneConflict() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        String bearer = token();
        String request = body("Concurrent_Alias");
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> submit = () -> {
                ready.countDown();
                assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                return mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + bearer)
                        .contentType("application/json").content(request)).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            try {
                assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            assertEquals(java.util.Set.of(201, 409), java.util.Set.of(
                    first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS)));
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM links WHERE short_code = 'Concurrent_Alias'", Integer.class));
    }

    @Test
    void invalidAndUnauthenticatedRequestsNeverInsert() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM links", Long.class);
        String bearer = token();
        for (String alias : new String[]{"ab", "a".repeat(33), "bad alias", "café", "a/b", "a?b", "a#b", "a%b"}) {
            mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + bearer)
                    .contentType("application/json").content(body(alias)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ALIAS"));
        }
        mvc.perform(post("/api/v1/links").contentType("application/json").content(body("Needs_Login")))
                .andExpect(status().isUnauthorized());
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM links", Long.class));
    }

    @Test
    void upgradingV2PreservesExistingCodesAndConstraint() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("legacy_alias_upgrade").target("2").load().migrate();
        jdbc.update("INSERT INTO legacy_alias_upgrade.links(original_url, short_code, created_at) VALUES (?, ?, now())",
                "https://example.com/legacy", "Legacy12");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("legacy_alias_upgrade").load().migrate();
        assertEquals("https://example.com/legacy", jdbc.queryForObject(
                "SELECT original_url FROM legacy_alias_upgrade.links WHERE short_code = 'Legacy12'", String.class));
        assertEquals(32, jdbc.queryForObject("SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE table_schema = 'legacy_alias_upgrade' AND table_name = 'links' AND column_name = 'short_code'", Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO legacy_alias_upgrade.links(original_url, short_code, created_at) VALUES (?, ?, now())",
                "https://example.com/duplicate", "Legacy12"));
    }

    private String body(String alias) throws Exception {
        return mapper.writeValueAsString(Map.of("originalUrl", "https://example.com/alias", "alias", alias));
    }

    private String token() {
        Instant now = Instant.now();
        var user = users.saveAndFlush(new com.example.shortener.user.entity.User(UUID.randomUUID() + "@example.com", "test-only-hash"));
        var claims = JwtClaimsSet.builder().issuer(TokenService.ISSUER).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
