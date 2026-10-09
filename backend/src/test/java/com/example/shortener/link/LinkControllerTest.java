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
@Import(SecurityConfiguration.class)
class LinkControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private LinkService service;
    @MockitoBean private com.example.shortener.security.CurrentUser currentUser;

    @Test
    void anonymousPostWithoutCsrfReturnsCreated() throws Exception {
        when(service.create(eq("https://example.com"), isNull())).thenReturn(new CreateLinkResponse(
                "https://example.com", "aB3dE7fG", "http://localhost:8080/r/aB3dE7fG"));
        mvc.perform(post("/api/v1/links").contentType("application/json")
                .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.shortCode").value("aB3dE7fG"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/r/aB3dE7fG"));
    }

    @Test
    void invalidInputReturnsStructuredError() throws Exception {
        when(service.create(eq("invalid"), isNull())).thenThrow(new InvalidUrlException());
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
}
