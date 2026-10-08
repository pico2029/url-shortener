package com.example.shortener.auth;

import com.example.shortener.shared.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {
    private RegistrationService registration;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        registration = mock(RegistrationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(registration, mock(LoginService.class), mock(com.example.shortener.user.UserRepository.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void invalidRequestsNeverReachTheService() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").isString())
                .andExpect(jsonPath("$.fieldErrors.password").isString());
        verifyNoInteractions(registration);
    }

    @Test
    void unexpectedFailuresDoNotExposeInternalDetails() throws Exception {
        when(registration.register(any())).thenThrow(new IllegalStateException("secret password hash SQL"));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"email\":\"a@example.com\",\"password\":\"una password lunga\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Il servizio non è disponibile. Riprova più tardi."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
