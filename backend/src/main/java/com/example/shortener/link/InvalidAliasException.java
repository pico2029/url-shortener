package com.example.shortener.link;

public class InvalidAliasException extends RuntimeException {
    public InvalidAliasException() {
        super("Use 3–32 ASCII letters, numbers, hyphens or underscores. Aliases are case-sensitive.");
    }
}
