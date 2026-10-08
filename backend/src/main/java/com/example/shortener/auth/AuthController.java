package com.example.shortener.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ProblemDetail;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registration;
    private final LoginService login;
    private final com.example.shortener.user.UserRepository users;

    public AuthController(RegistrationService registration, LoginService login,
            com.example.shortener.user.UserRepository users) {
        this.registration = registration;
        this.login = login;
        this.users = users;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a Bearer JWT valid for one hour.")
    @ApiResponse(responseCode = "200", description = "Logged in successfully")
    @ApiResponse(responseCode = "401", description = "Incorrect email or password")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return login.login(request);
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    @Operation(summary = "Authenticated user")
    public RegisterResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.oauth2.jwt.Jwt jwt) {
        try {
            return users.findById(java.util.UUID.fromString(jwt.getSubject())).map(RegisterResponse::from)
                    .orElseThrow(InvalidCredentialsException::new);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCredentialsException();
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an account", description = "Normalized, unique email; password of 15 to 128 UTF-16 code units. Does not create a session.")
    @ApiResponse(responseCode = "201", description = "Account created without a session",
            content = @Content(schema = @Schema(implementation = RegisterResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid data (VALIDATION_ERROR with fieldErrors) or malformed JSON (INVALID_JSON)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":400,\"detail\":\"Check the highlighted fields.\",\"code\":\"VALIDATION_ERROR\",\"fieldErrors\":{\"email\":\"Enter a valid email address.\"}}")))
    @ApiResponse(responseCode = "409", description = "Email already registered: EMAIL_ALREADY_REGISTERED",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Internal error: INTERNAL_ERROR",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registration.register(request);
    }
}
