package com.example.shortener.analytics;

import com.example.shortener.analytics.repository.ClickRepository;
import com.example.shortener.analytics.service.ClickRecorder;
import com.example.shortener.analytics.service.StatisticsService;
import com.example.shortener.link.repository.LinkRepository;
import com.example.shortener.link.service.LinkService;
import com.example.shortener.security.service.TokenService;
import com.example.shortener.user.entity.User;
import com.example.shortener.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.data.redis.connect-timeout=1s", "spring.data.redis.timeout=1s"})
@AutoConfigureMockMvc
@Testcontainers
class StatisticsIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.6-alpine").withExposedPorts(6379);
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
    @Autowired private MockMvc mvc;
    @Autowired private LinkService links;
    @MockitoSpyBean private LinkRepository linkRepository;
    @MockitoSpyBean private ClickRepository clicks;
    @Autowired private StatisticsService statistics;
    @Autowired private ClickRecorder recorder;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired @Qualifier("clickExecutor") private ThreadPoolTaskExecutor executor;
    @MockitoBean private Clock clock;
    private UUID owner;
    private String token;
    private final Instant now = Instant.parse("2030-01-31T00:00:00Z");

    @BeforeEach
    void setUp() {
        drain();
        jdbc.update("DELETE FROM links");
        users.deleteAll();
        when(clock.instant()).thenReturn(now);
        owner = users.saveAndFlush(new User("stats@example.com", "unused")).getId();
        token = tokens.issue(owner).accessToken();
    }

    private void drain() {
        try {
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Click recorder did not drain", exception);
        }
    }

    private long create(String alias) {
        links.create("https://example.com/" + alias, alias, owner);
        return linkRepository.findByShortCode(alias).orElseThrow().getId();
    }

    @Test
    void cachedAndUncachedGetsCountOnceButHeadAndErrorsDoNot() throws Exception {
        long id = create("StatsCache");
        clearInvocations(linkRepository);
        mvc.perform(get("/r/StatsCache")).andExpect(status().isFound());
        mvc.perform(get("/r/StatsCache")).andExpect(status().isFound());
        mvc.perform(head("/r/StatsCache")).andExpect(status().isFound());
        mvc.perform(get("/r/missing-stats")).andExpect(status().isNotFound());
        links.create("https://example.com/expired", "StatsExpired", owner, now.plusSeconds(1));
        when(clock.instant()).thenReturn(now.plusSeconds(1));
        mvc.perform(get("/r/StatsExpired")).andExpect(status().isGone());
        await().atMost(Duration.ofSeconds(5)).until(() -> clicks.total(id) == 2);
        drain();
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM click_events", Long.class));
        verify(linkRepository, times(1)).findByShortCode("StatsCache");
        mvc.perform(get("/api/v1/links/StatsCache/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalClicks").value(2)).andExpect(jsonPath("$.daily[29].clicks").value(2));
    }

    @Test
    void utcBoundariesZeroDaysAndLifetimeTotalAreConsistent() throws Exception {
        long id = create("StatsDays");
        clicks.insert(id, Instant.parse("2029-12-31T23:59:59Z")); // outside the window
        clicks.insert(id, Instant.parse("2030-01-01T23:59:59.999999Z"));
        clicks.insert(id, Instant.parse("2030-01-02T00:00:00Z"));
        clicks.insert(id, Instant.parse("2030-01-30T23:59:59.999999Z"));
        clicks.insert(id, now);
        mvc.perform(get("/api/v1/links/StatsDays/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.from").value("2030-01-02")).andExpect(jsonPath("$.to").value("2030-01-31"))
                .andExpect(jsonPath("$.totalClicks").value(5)).andExpect(jsonPath("$.daily.length()").value(30))
                .andExpect(jsonPath("$.daily[0].clicks").value(1)).andExpect(jsonPath("$.daily[1].clicks").value(0))
                .andExpect(jsonPath("$.daily[28].clicks").value(1)).andExpect(jsonPath("$.daily[29].clicks").value(1));
        create("StatsZero");
        var zero = statistics.get("StatsZero", owner);
        assertEquals(0, zero.totalClicks());
        assertTrue(zero.daily().stream().allMatch(day -> day.clicks() == 0));
    }

    @Test
    void totalAndDailyShareASnapshotWhenAnotherClickCommitsBetweenQueries() {
        long id = create("StatsSnapshot");
        clicks.insert(id, now);
        doAnswer(invocation -> {
            long total = (long) invocation.callRealMethod();
            try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(), POSTGRES.getPassword());
                    var insert = connection.prepareStatement("INSERT INTO click_events (link_id, occurred_at) VALUES (?, ?)")) {
                insert.setLong(1, id);
                insert.setTimestamp(2, java.sql.Timestamp.from(now));
                insert.executeUpdate();
            }
            return total;
        }).when(clicks).total(id);
        var result = statistics.get("StatsSnapshot", owner);
        assertEquals(1, result.totalClicks());
        assertEquals(1, result.daily().get(29).clicks());
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM click_events WHERE link_id = ?", Long.class, id));
    }

    @Test
    void restrictsAccessWithoutRevealingOtherOwnersAndIncludesExpiredLinks() throws Exception {
        create("StatsOwned");
        var other = users.saveAndFlush(new User("other-stats@example.com", "unused"));
        String otherToken = tokens.issue(other.getId()).accessToken();
        links.create("https://example.com/anonymous", "StatsAnonymous");
        // Anonymous GETs are recorded, but their statistics remain private.
        mvc.perform(get("/r/StatsAnonymous")).andExpect(status().isFound());
        drain();
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM click_events", Long.class));
        for (String code : new String[]{"StatsOwned", "StatsAnonymous", "missing"}) {
            mvc.perform(get("/api/v1/links/" + code + "/stats").header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
        }
        mvc.perform(get("/api/v1/links/StatsOwned/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/links/StatsOwned/stats").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/links/StatsOwned/stats").header("Authorization", "Bearer " + tokens.issue(UUID.randomUUID()).accessToken()))
                .andExpect(status().isUnauthorized());
        links.create("https://example.com/expired", "StatsExpiredAccess", owner, now.plusSeconds(1));
        when(clock.instant()).thenReturn(now.plusSeconds(1));
        mvc.perform(get("/api/v1/links/StatsExpiredAccess/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalClicks").value(0));
    }

    @Test
    void deletionCascadesAndPendingEventsCannotReachReusedAlias() {
        long oldId = create("StatsReuse");
        clicks.insert(oldId, now);
        links.delete("StatsReuse", owner);
        long newId = create("StatsReuse");
        assertNotEquals(oldId, newId);
        recorder.record(oldId, now); // delayed event from the original link
        drain();
        assertEquals(0, statistics.get("StatsReuse", owner).totalClicks());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM click_events", Long.class));
    }

    @Test
    void concurrentRequestsPreserveAllAcceptedEvents() throws Exception {
        long id = create("StatsConcurrent");
        try (var requests = Executors.newFixedThreadPool(4)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) results.add(requests.submit(() -> {
                try { mvc.perform(get("/r/StatsConcurrent")).andExpect(status().isFound()); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            }));
            for (var result : results) result.get(5, TimeUnit.SECONDS);
        }
        await().atMost(Duration.ofSeconds(5)).until(() -> clicks.total(id) == 20);
        assertEquals(20, statistics.get("StatsConcurrent", owner).totalClicks());
    }

    @Test
    void writeFailuresPreserveRedirectButStatisticsFailuresReturn503() throws Exception {
        long id = create("StatsFailure");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private details"))
                .when(clicks).insert(eq(id), any());
        mvc.perform(get("/r/StatsFailure")).andExpect(status().isFound());
        drain();
        assertEquals(0, clicks.total(id));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private details"))
                .when(clicks).total(id);
        mvc.perform(get("/api/v1/links/StatsFailure/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private details"))));
    }
}
