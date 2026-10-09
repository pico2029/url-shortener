package com.example.shortener.link.exception;

public class InvalidExpirationException extends RuntimeException {
    public InvalidExpirationException() { super("Choose an expiration in the future."); }
}
