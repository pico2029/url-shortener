package com.example.shortener.link.exception;

public class ExpirationAuthenticationException extends RuntimeException {
    public ExpirationAuthenticationException() { super("Log in to set an expiration."); }
}
