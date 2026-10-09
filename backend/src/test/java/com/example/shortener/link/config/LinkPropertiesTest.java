package com.example.shortener.link.config;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class LinkPropertiesTest {
    @Test
    void usesConfiguredOriginAndRemovesTrailingSlash() {
        assertEquals("https://short.example/r/abcd1234",
                new LinkProperties(URI.create("https://short.example/")).shortUrl("abcd1234"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com", "https://example.com/path", "https://user@example.com",
            "https://example.com?q=1", "https://example.com#fragment", "http://localhost:65536", "http://localhost:0"})
    void failsForInvalidOrigin(String value) {
        assertThrows(IllegalArgumentException.class, () -> new LinkProperties(URI.create(value)));
    }
}
