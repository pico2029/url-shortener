package com.example.shortener.link;

import com.example.shortener.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RedirectController.class)
@Import(SecurityConfiguration.class)
class RedirectControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private LinkResolutionService service;

    @Test
    void redirectsAnonymouslyWithoutForwardingShortUrlQueryParameters() throws Exception {
        String destination = "https://example.com/path%2Fpart?q=one%20two#section";
        when(service.resolve("aB3dE7fG")).thenReturn(destination);
        mvc.perform(get("/r/aB3dE7fG").queryParam("extra", "ignored"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", destination))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
    }

    @Test
    void encodesUnicodeWithoutDoubleEncodingExistingEscapes() throws Exception {
        when(service.resolve("aB3dE7fG")).thenReturn("http://localhost:3000/café/%20?q=è%2F#é");
        mvc.perform(get("/r/aB3dE7fG"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/caf%C3%A9/%20?q=%C3%A8%2F#%C3%A9"));
    }

    @Test
    void missingCodeReturnsAnEnglishHtmlPageWithoutReflectingTheCode() throws Exception {
        when(service.resolve("missing-code")).thenThrow(new LinkNotFoundException());
        mvc.perform(get("/r/missing-code").accept("text/html"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(allOf(containsString("<html lang=\"en\">"),
                        containsString("<h1>Link not found</h1>"), containsString("href=\"/\">Back to home"),
                        not(containsString("missing-code")))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void databaseAndTransactionFailuresReturn503WithoutInternalDetails(boolean transaction) throws Exception {
        RuntimeException failure = transaction ? new CannotCreateTransactionException("secret database details")
                : new DataAccessResourceFailureException("secret database details");
        when(service.resolve("aB3dE7fG")).thenThrow(failure);
        mvc.perform(get("/r/aB3dE7fG"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(allOf(containsString("<h1>Service unavailable</h1>"),
                        containsString("We couldn't open this link. Please try again later."),
                        not(containsString("secret")))));
    }

    @Test
    void headReturnsRedirectHeadersWithoutABody() throws Exception {
        when(service.resolve("aB3dE7fG")).thenReturn("https://example.com/path?q=one#fragment");
        mvc.perform(head("/r/aB3dE7fG"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/path?q=one#fragment"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
    }

    @Test
    void headErrorPagesHaveTheSameHeadersAndNoBody() throws Exception {
        when(service.resolve("missing")).thenThrow(new LinkNotFoundException());
        when(service.resolve("unavailable")).thenThrow(new CannotCreateTransactionException("secret"));
        mvc.perform(head("/r/missing"))
                .andExpect(status().isNotFound()).andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().string(""));
        mvc.perform(head("/r/unavailable"))
                .andExpect(status().isServiceUnavailable()).andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().string(""));
    }

    @Test
    void protectedRoutesAndInvalidBearerTokensKeepTheirSecurityBehavior() throws Exception {
        mvc.perform(get("/api/v1/links")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/r/aB3dE7fG")).andExpect(status().isForbidden());
        mvc.perform(get("/r/aB3dE7fG").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
