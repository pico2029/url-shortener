package com.example.shortener.link;

import com.example.shortener.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LinkController.class)
@Import({SecurityConfiguration.class, AliasValidator.class})
class LinkControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private LinkService service;
    @Autowired private org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;

    @Test
    void anonymousPostWithoutCsrfReturnsCreated() throws Exception {
        when(service.create("https://example.com", null)).thenReturn(new CreateLinkResponse(
                "https://example.com", "aB3dE7fG", "http://localhost:8080/r/aB3dE7fG"));
        mvc.perform(post("/api/v1/links").contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.shortCode").value("aB3dE7fG"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/r/aB3dE7fG"));
    }

    @Test
    void invalidInputReturnsStructuredError() throws Exception {
        when(service.create("invalid", null)).thenThrow(new InvalidUrlException());
        mvc.perform(post("/api/v1/links").contentType("application/json").content("{\"originalUrl\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_URL"))
                .andExpect(jsonPath("$.field").value("originalUrl"));
    }

    @Test
    void malformedJsonNeverCallsService() throws Exception {
        mvc.perform(post("/api/v1/links").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void persistenceFailureDoesNotExposeDetails() throws Exception {
        when(service.create(any(), any())).thenThrow(new LinkUnavailableException(new RuntimeException("secret")));
        mvc.perform(post("/api/v1/links").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void otherEndpointsRemainProtected() throws Exception {
        mvc.perform(get("/api/v1/links").accept("application/json")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/other").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAliasRequiresLoginBeforeValidationOrPersistence() throws Exception {
        for (String alias : new String[]{"Promo", "bad alias"}) {
            mvc.perform(post("/api/v1/links").contentType("application/json")
                    .content("{\"originalUrl\":\"invalid\",\"alias\":\"" + alias + "\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Please log in to choose a custom alias."));
        }
        verifyNoInteractions(service);
    }

    @Test
    void anonymousEmptyAliasesRemainPublic() throws Exception {
        when(service.create("https://example.com", null)).thenReturn(new CreateLinkResponse(
                "https://example.com", "aB3dE7fG", "http://localhost:8080/r/aB3dE7fG"));
        for (String alias : new String[]{"null", "\"\"", "\"  \""}) {
            mvc.perform(post("/api/v1/links").contentType("application/json")
                    .content("{\"originalUrl\":\"https://example.com\",\"alias\":" + alias + "}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void authenticatedAliasIsNormalizedAndCreated() throws Exception {
        when(service.create("https://example.com", "Promo")).thenReturn(new CreateLinkResponse(
                "https://example.com", "Promo", "http://localhost:8080/r/Promo"));
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token(false))
                .contentType("application/json").content("{\"originalUrl\":\"https://example.com\",\"alias\":\" Promo \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.shortCode").value("Promo"));
    }

    @Test
    void invalidAndOccupiedAliasesUseFieldErrors() throws Exception {
        when(service.create("https://example.com", "bad alias")).thenThrow(new InvalidAliasException());
        when(service.create("https://example.com", "Promo")).thenThrow(new AliasAlreadyTakenException());
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token(false))
                .contentType("application/json").content("{\"originalUrl\":\"https://example.com\",\"alias\":\"bad alias\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ALIAS"))
                .andExpect(jsonPath("$.field").value("alias"));
        mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + token(false))
                .contentType("application/json").content("{\"originalUrl\":\"https://example.com\",\"alias\":\"Promo\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALIAS_ALREADY_TAKEN"))
                .andExpect(jsonPath("$.field").value("alias"));
    }

    @Test
    void nonStringAliasesAreNeverCoerced() throws Exception {
        for (String alias : new String[]{"123", "true", "{}", "[]", "[\"Promo\"]"}) {
            mvc.perform(post("/api/v1/links").contentType("application/json")
                    .content("{\"originalUrl\":\"https://example.com\",\"alias\":" + alias + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void invalidAndExpiredBearerTokensAreRejectedEvenWithoutAlias() throws Exception {
        for (String bearer : new String[]{"invalid-token", token(true)}) {
            for (String alias : new String[]{"", ",\"alias\":\"Promo\""}) {
                mvc.perform(post("/api/v1/links").header("Authorization", "Bearer " + bearer)
                        .contentType("application/json").content("{\"originalUrl\":\"https://example.com\"" + alias + "}"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
            }
        }
        verifyNoInteractions(service);
    }

    private String token(boolean expired) {
        java.time.Instant now = java.time.Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(com.example.shortener.security.TokenService.ISSUER).subject(java.util.UUID.randomUUID().toString())
                .issuedAt(now.minusSeconds(600)).expiresAt(now.plusSeconds(expired ? -300 : 3600)).build();
        return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
