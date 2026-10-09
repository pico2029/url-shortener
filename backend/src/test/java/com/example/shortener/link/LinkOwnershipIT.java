package com.example.shortener.link;

import com.example.shortener.security.TokenService;
import com.example.shortener.user.User;
import com.example.shortener.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class LinkOwnershipIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokens;
    @Autowired private org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM links");
        users.deleteAll();
        userA = users.saveAndFlush(new User("a@example.com", "unused"));
        userB = users.saveAndFlush(new User("b@example.com", "unused"));
        tokenA = tokens.issue(userA.getId()).accessToken();
        tokenB = tokens.issue(userB.getId()).accessToken();
    }

    private JsonNode create(String token, String url, UUID attemptedOwner) throws Exception {
        var request = post("/api/v1/links").contentType("application/json")
                .content(mapper.writeValueAsBytes(Map.of("originalUrl", url, "ownerId", attemptedOwner.toString())));
        if (token != null) request.header("Authorization", "Bearer " + token);
        var result = mvc.perform(request).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void listsOnlyTheCurrentUsersLinksAndNeverAcceptsAnotherOwner() throws Exception {
        create(null, "https://example.com/anonymous", userA.getId());
        var linkA = create(tokenA, "https://example.com/a", userB.getId());
        create(tokenB, "https://example.com/b", userA.getId());
        mvc.perform(get("/api/v1/links").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].shortCode").value(linkA.get("shortCode").asText()))
                .andExpect(jsonPath("$.items[0].originalUrl").value("https://example.com/a"))
                .andExpect(jsonPath("$.items[0].shortUrl").isString())
                .andExpect(jsonPath("$.items[0].createdAt").isString())
                .andExpect(jsonPath("$.items[0].status").value("SAVED"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/links").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].originalUrl").value("https://example.com/b"));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM links WHERE owner_id IS NULL", Long.class));
        mvc.perform(get("/api/v1/links").param("userId", userB.getId().toString()).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customAliasUsesAuthenticatedOwnerAndAppearsOnlyInTheirList() throws Exception {
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + tokenA)
                .contentType("application/json").content(mapper.writeValueAsBytes(Map.of(
                        "originalUrl", "https://example.com/alias", "alias", " Personal-Promo ", "ownerId", userB.getId()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.shortCode").value("Personal-Promo"));
        assertEquals(userA.getId(), jdbc.queryForObject(
                "SELECT owner_id FROM links WHERE short_code = 'Personal-Promo'", UUID.class));
        mvc.perform(get("/api/v1/links").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].shortCode").value("Personal-Promo"));
        mvc.perform(get("/api/v1/links").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/r/Personal-Promo")).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/alias"));
    }

    @Test
    void paginatesWithStableNewestFirstOrderAndReturnsEmptyPages() throws Exception {
        for (int i = 0; i < 3; i++) create(tokenA, "https://example.com/" + i, userA.getId());
        jdbc.update("UPDATE links SET created_at = ?", java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        mvc.perform(get("/api/v1/links?page=0&size=2").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].originalUrl").value("https://example.com/2"))
                .andExpect(jsonPath("$.items[1].originalUrl").value("https://example.com/1"))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/links?page=1&size=2").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].originalUrl").value("https://example.com/0"));
        mvc.perform(get("/api/v1/links?page=5&size=2").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/links").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsBadPaginationAndUnauthorizedRequests() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=101", "page=", "size=", "page=0&page=1", "page=x", "page=9999999999999999999"}) {
            mvc.perform(get("/api/v1/links?" + query).header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/v1/links")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        var expiredClaims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(TokenService.ISSUER).subject(userA.getId().toString())
                .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build();
        var expiredToken = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), expiredClaims)).getTokenValue();
        for (String token : new String[]{"invalid", expiredToken, tokens.issue(UUID.randomUUID()).accessToken()}) {
            mvc.perform(get("/api/v1/links").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content("{\"originalUrl\":\"https://example.com\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM links", Long.class));
    }

    @Test
    void migrationPreservesPreviouslyAnonymousLinksAndUsers() {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("legacy").defaultSchema("legacy");
        config.target("2").load().migrate();
        jdbc.update("INSERT INTO legacy.users (id,email,password_hash,created_at) VALUES (?, 'legacy@example.com','preserved',now())", UUID.randomUUID());
        jdbc.update("INSERT INTO legacy.links (original_url,short_code,created_at) VALUES ('https://example.com/legacy','LEGACY01',now())");
        config.target("latest").load().migrate();
        assertEquals("https://example.com/legacy", jdbc.queryForObject("SELECT original_url FROM legacy.links", String.class));
        assertNull(jdbc.queryForObject("SELECT owner_id FROM legacy.links", UUID.class));
        assertEquals("preserved", jdbc.queryForObject("SELECT password_hash FROM legacy.users", String.class));
    }
}
