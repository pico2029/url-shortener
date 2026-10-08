package com.example.shortener.auth;

import com.example.shortener.security.TokenService;
import com.example.shortener.user.User;
import com.example.shortener.user.UserRepository;
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
