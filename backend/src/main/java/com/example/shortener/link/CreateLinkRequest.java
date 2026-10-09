package com.example.shortener.link;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;

public record CreateLinkRequest(String originalUrl,
        @JsonDeserialize(using = AliasDeserializer.class) String alias,
        @JsonDeserialize(using = ExpirationDeserializer.class) Instant expiresAt) {
    public CreateLinkRequest(String originalUrl) { this(originalUrl, null, null); }
    public CreateLinkRequest(String originalUrl, String alias) { this(originalUrl, alias, null); }
}
