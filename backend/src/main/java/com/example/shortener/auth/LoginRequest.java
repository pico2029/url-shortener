package com.example.shortener.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public class LoginRequest {
    @NotBlank(message = "Inserisci un indirizzo email.")
    @Size(max = 254, message = "L'email può contenere al massimo 254 caratteri.")
    private String email;
    @NotNull(message = "Inserisci una password.")
    @Size(min = 1, max = 128, message = "Inserisci una password valida.")
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
