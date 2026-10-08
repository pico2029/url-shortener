package com.example.shortener.link;

public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException() {
        super("Inserisci un URL HTTP/HTTPS valido, completo di host, entro 8192 caratteri.");
    }
}
