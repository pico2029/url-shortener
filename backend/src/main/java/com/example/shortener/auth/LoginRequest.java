package com.example.shortener.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public class LoginRequest {
    @NotBlank(message = "Enter an email address.")
    @Size(max = 254, message = "Your email address must not exceed 254 characters.")
    private String email;
    @NotNull(message = "Enter a password.")
    @Size(min = 1, max = 128, message = "Enter a valid password.")
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
