package com.example.shortener.auth.service;

import com.example.shortener.auth.dto.RegisterRequest;
import com.example.shortener.auth.dto.RegisterResponse;
import com.example.shortener.auth.exception.DuplicateEmailException;

import com.example.shortener.user.entity.User;
import com.example.shortener.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public RegistrationService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        try {
            User user = users.saveAndFlush(new User(request.getEmail(), encoder.encode(request.getPassword())));
            return RegisterResponse.from(user);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uk_users_email".equals(violation.getConstraintName())) {
                    throw new DuplicateEmailException();
                }
            }
            throw exception;
        }
    }
}
