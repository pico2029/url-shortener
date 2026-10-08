package com.example.shortener.link;

public class LinkUnavailableException extends RuntimeException {
    LinkUnavailableException(Throwable cause) { super("Link creation unavailable", cause); }
}
