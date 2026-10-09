package com.example.shortener.link.exception;

public class AliasAlreadyTakenException extends RuntimeException {
    public AliasAlreadyTakenException() {
        super("This alias is already taken. Choose another alias.");
    }
}
