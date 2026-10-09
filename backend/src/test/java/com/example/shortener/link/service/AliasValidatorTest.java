package com.example.shortener.link.service;

import com.example.shortener.link.exception.InvalidAliasException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AliasValidatorTest {
    private final AliasValidator validator = new AliasValidator();

    @Test
    void acceptsBoundariesAlphabetAndExactCase() {
        for (String alias : new String[]{"a-_", "A".repeat(32), "Promo-2026_test", "promo"}) {
            assertEquals(alias, validator.validate(alias));
        }
        assertEquals("Promo", validator.validate(" \t\u2003Promo\u3000 "));
    }

    @Test
    void emptyAliasesAreAbsent() {
        for (String alias : new String[]{null, "", " \t\r\n\u2003\u3000"}) {
            assertNull(validator.validate(alias));
        }
    }

    @Test
    void rejectsInvalidLengthUnicodeWhitespaceAndUrlCharacters() {
        for (String alias : new String[]{"ab", "a".repeat(33), "bad alias", "café", "😀abc",
                "a/b", "a?b", "a#b", "a%b", "a.b", "a+b", "\u00a0Promo\u00a0", "a\tb"}) {
            assertThrows(InvalidAliasException.class, () -> validator.validate(alias), alias);
        }
    }
}
