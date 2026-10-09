package com.example.shortener.auth.service;

import com.example.shortener.auth.dto.LoginRequest;
import com.example.shortener.auth.dto.LoginResponse;
import com.example.shortener.auth.exception.InvalidCredentialsException;

import com.example.shortener.security.service.TokenService;
import com.example.shortener.user.entity.User;
import com.example.shortener.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("dummy password for timing");
    }

    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmail(request.getEmail()).orElse(null);
        boolean matches = encoder.matches(request.getPassword(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) throw new InvalidCredentialsException();
        return tokens.issue(user.getId());
    }
}
