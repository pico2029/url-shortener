package com.example.shortener.link.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class ExpirationDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (Instant) context.handleUnexpectedToken(Instant.class, parser);
        }
        try {
            return OffsetDateTime.parse(parser.getText()).toInstant();
        } catch (DateTimeParseException exception) {
            return (Instant) context.handleWeirdStringValue(Instant.class, parser.getText(),
                    "Use an ISO-8601 date and time with Z or an offset.");
        }
    }
}
