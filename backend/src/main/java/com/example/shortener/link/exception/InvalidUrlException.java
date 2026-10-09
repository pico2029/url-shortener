package com.example.shortener.link.exception;

public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException() {
        super("Enter a valid HTTP/HTTPS URL with a host, up to 8,192 characters.");
    }
}
