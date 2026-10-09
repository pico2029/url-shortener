package com.example.shortener.link;

public class AliasAuthenticationException extends RuntimeException {
    public AliasAuthenticationException() {
        super("Please log in to choose a custom alias.");
    }
}
