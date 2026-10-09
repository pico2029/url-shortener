package com.example.shortener.link.exception;

import com.example.shortener.link.entity.Link;

public class LinkUnavailableException extends RuntimeException {
    public LinkUnavailableException(Throwable cause) { super("Link creation unavailable", cause); }
}
