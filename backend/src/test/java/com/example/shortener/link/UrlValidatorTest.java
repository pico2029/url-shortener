package com.example.shortener.link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {
    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/path?q=hello#part", "http://localhost:3000",
            "http://192.168.1.1", "http://[::1]:8080", "HTTPS://example.com", "https://example.com/%20"})
    void acceptsHttpDestinations(String url) { assertEquals(url, validator.validate(url)); }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "example.com", "/relative", "ftp://example.com", "https:///path",
            "https://example.com/a b", "https://example.com:abc", "http://localhost:65536",
            "http://localhost:0", "http://localhost:-1", "http://localhost:", "https://example.com/%ZZ"})
    void rejectsInvalidDestinations(String url) {
        assertThrows(InvalidUrlException.class, () -> validator.validate(url));
    }

    @Test
    void trimsAndEnforcesLength() {
        assertEquals("https://example.com", validator.validate("  https://example.com \n"));
        assertThrows(InvalidUrlException.class, () -> validator.validate(null));
        String boundary = "https://example.com/" + "a".repeat(8192 - 20);
        assertEquals(8192, boundary.length());
        assertEquals(boundary, validator.validate(boundary));
        assertThrows(InvalidUrlException.class, () -> validator.validate(boundary + "a"));
    }
}
