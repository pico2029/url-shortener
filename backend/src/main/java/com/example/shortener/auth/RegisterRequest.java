package com.example.shortener.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

public class RegisterRequest {
    @NotBlank(message = "Inserisci un indirizzo email.")
    @Email(message = "Inserisci un indirizzo email valido.")
    @Size(max = 254, message = "L'email può contenere al massimo 254 caratteri.")
    @Schema(description = "Email normalizzata con strip e lowercase Locale.ROOT prima della validazione", maxLength = 254, example = "utente@example.com")
    private String email;

    @NotNull(message = "Inserisci una password.")
    @Size(min = 15, max = 128, message = "La password deve contenere da 15 a 128 caratteri.")
    @Schema(description = "Password preservata senza trimming; lunghezza in unità UTF-16", minLength = 15, maxLength = 128, format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
