package com.example.shortener.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

public class RegisterRequest {
    @NotBlank(message = "Enter an email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 30, message = "Your email address must not exceed 30 characters.")
    @Schema(description = "Email normalized with strip and Locale.ROOT lowercase before validation", maxLength = 30, example = "user@example.com")
    private String email;

    @NotNull(message = "Enter a password.")
    @Size(min = 6, max = 18, message = "Your password must contain 6 to 18 characters.")
    @Schema(description = "Password preserved without trimming; length in UTF-16 code units", minLength = 6, maxLength = 18, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
