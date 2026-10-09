package com.example.shortener.link;

public class InvalidExpirationException extends RuntimeException {
    public InvalidExpirationException() { super("Choose an expiration in the future."); }
}
