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
    @Size(max = 254, message = "Your email address must not exceed 254 characters.")
    @Schema(description = "Email normalized with strip and Locale.ROOT lowercase before validation", maxLength = 254, example = "user@example.com")
    private String email;

    @NotNull(message = "Enter a password.")
    @Size(min = 15, max = 128, message = "Your password must contain 15 to 128 characters.")
    @Schema(description = "Password preserved without trimming; length in UTF-16 code units", minLength = 15, maxLength = 128, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
