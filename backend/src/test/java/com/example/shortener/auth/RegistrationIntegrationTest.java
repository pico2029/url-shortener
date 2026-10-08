package com.example.shortener.auth;

import com.example.shortener.user.User;
import com.example.shortener.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
class RegistrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;

    @BeforeEach
    void clean() { users.deleteAll(); }

    private ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(mapper.writeValueAsBytes(Map.of("email", email, "password", password))));
    }

    @Test
    void createsAnonymousAccountAndReturnsOnlyPublicFields() throws Exception {
        MvcResult result = register("  USER@Example.COM ", "  password lunga 🔐  ")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.id").isString()).andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        assertNull(result.getResponse().getHeader("Set-Cookie"));
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals(3, response.size());
        assertTrue(response.get("createdAt").asText().endsWith("Z"));
        User user = users.findAll().getFirst();
        assertTrue(encoder.matches("  password lunga 🔐  ", user.getPasswordHash()));
        assertFalse(encoder.matches("password lunga 🔐", user.getPasswordHash()));
    }

    @Test
    void rejectsNormalizedDuplicate() throws Exception {
        register("user@example.com", "a".repeat(15)).andExpect(status().isCreated());
        register(" USER@EXAMPLE.COM ", "b".repeat(15)).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertEquals(1, users.count());
    }

    @Test
    void validatesInputsAndMalformedJsonWithoutPersisting() throws Exception {
        for (String password : List.of("", "a".repeat(14), "a".repeat(129))) {
            register("a@example.com", password).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").isString());
        }
        register("invalid", "a".repeat(15)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isString());
        register("a".repeat(250) + "@example.com", "a".repeat(15)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").isString())
                .andExpect(jsonPath("$.fieldErrors.password").isString());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        assertEquals(0, users.count());
    }

    @Test
    void acceptsPasswordBoundariesAndSpaces() throws Exception {
        for (int length : new int[]{15, 128}) {
            register("user" + length + "@example.com", " ".repeat(length)).andExpect(status().isCreated());
        }
    }

    @Test
    void exposesHealthButKeepsOtherRoutesProtected() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        for (String path : List.of("/api/v1/links", "/r/example", "/v3/api-docs", "/swagger-ui/index.html")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mvc.perform(post("/api/v1/links").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logsInWithExistingAccountAndAuthorizesMe() throws Exception {
        register("user@example.com", "  una password lunga  ").andExpect(status().isCreated());
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(mapper.writeValueAsBytes(Map.of("email", " USER@EXAMPLE.COM ", "password", "  una password lunga  "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isString()).andReturn();
        assertNull(result.getResponse().getHeader("Set-Cookie"));
        String token = mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAndUnknownCredentialsHaveTheSameResponse() throws Exception {
        register("user@example.com", "  una password lunga  ").andExpect(status().isCreated());
        for (String email : List.of("user@example.com", "unknown@example.com")) {
            mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                    .content(mapper.writeValueAsBytes(Map.of("email", email, "password", "una password lunga"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(com.example.shortener.security.TokenService.ISSUER).subject(java.util.UUID.randomUUID().toString())
                .issuedAt(java.time.Instant.now().minusSeconds(7200)).expiresAt(java.time.Instant.now().minusSeconds(3600)).build();
        String token = jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRequestsCreateOneAccount() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> task = () -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return register("race@example.com", "a".repeat(15)).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(task);
            Future<Integer> second = executor.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(List.of(201, 409), java.util.stream.Stream.of(first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1, users.count());
    }
}
