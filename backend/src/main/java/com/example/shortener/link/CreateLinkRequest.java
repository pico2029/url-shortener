package com.example.shortener.link;

public record CreateLinkRequest(String originalUrl,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = AliasDeserializer.class) String alias) {
    public CreateLinkRequest(String originalUrl) {
        this(originalUrl, null);
    }
}
