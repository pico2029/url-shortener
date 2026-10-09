package com.example.shortener.auth;

import com.example.shortener.user.User;
import com.example.shortener.user.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.sql.SQLException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final String encoderId = "pbkdf2@SpringSecurity_v5_8";
    private final PasswordEncoder encoder = new DelegatingPasswordEncoder(encoderId,
            Map.of(encoderId, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    private final RegistrationService service = new RegistrationService(users, encoder);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private RegisterRequest request(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void normalizesEmailAndHashesPasswordWithoutTrimmingIt() {
        RegisterRequest request = request("  USER@Example.COM  ", "  una password lunga 🔐  ");
        when(users.saveAndFlush(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertEquals("user@example.com", user.getEmail());
            assertNotEquals(request.getPassword(), user.getPasswordHash());
            assertTrue(user.getPasswordHash().startsWith("{" + encoderId + "}"));
            assertTrue(encoder.matches(request.getPassword(), user.getPasswordHash()));
            assertFalse(encoder.matches(request.getPassword().strip(), user.getPasswordHash()));
            return user;
        });
        RegisterResponse response = service.register(request);
        assertNotNull(response.id());
        assertNotNull(response.createdAt());
        assertEquals("user@example.com", response.email());
    }

    @Test
    void validatesBoundariesAndRequiredFields() {
        for (int length : new int[]{6, 18}) {
            assertTrue(validator.validate(request("a@example.com", "a".repeat(length))).isEmpty());
        }
        for (String password : new String[]{null, "", "a".repeat(5), "a".repeat(19)}) {
            assertFalse(validator.validate(request("a@example.com", password)).isEmpty());
        }
        for (String email : new String[]{null, "", " ", "invalid", "a".repeat(19) + "@example.com"}) {
            assertFalse(validator.validate(request(email, "a".repeat(15))).isEmpty());
        }
        String maximumEmail = "a".repeat(18) + "@example.com";
        assertEquals(30, maximumEmail.length());
        assertTrue(validator.validate(request(maximumEmail, "a".repeat(15))).isEmpty());
        assertFalse(validator.validate(request("a" + maximumEmail, "a".repeat(15))).isEmpty());
        assertTrue(validator.validate(request("a@example.com", " ".repeat(15))).isEmpty());
        assertTrue(validator.validate(request("a@example.com", "🔐".repeat(8))).isEmpty());
    }

    @Test
    void mapsOnlyTheEmailConstraintToConflict() {
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("db",
                new ConstraintViolationException("db", new SQLException(), "uk_users_email")));
        assertThrows(DuplicateEmailException.class,
                () -> service.register(request("a@example.com", "a".repeat(15))));
    }

    @Test
    void preservesOtherDatabaseErrors() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("db",
                new ConstraintViolationException("db", new SQLException(), "other_constraint"));
        when(users.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.register(request("a@example.com", "a".repeat(15)))));
    }
}
