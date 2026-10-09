package com.example.shortener.security.service;

import com.example.shortener.auth.exception.InvalidCredentialsException;
import com.example.shortener.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class CurrentUser {
    private final UserRepository users;

    public CurrentUser(UserRepository users) { this.users = users; }

    public UUID optionalId(Jwt jwt) {
        return jwt == null ? null : requiredId(jwt);
    }

    public UUID requiredId(Jwt jwt) {
        if (jwt == null) throw new InvalidCredentialsException();
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidCredentialsException();
        }
        if (!users.existsById(id)) throw new InvalidCredentialsException();
        return id;
    }
}
