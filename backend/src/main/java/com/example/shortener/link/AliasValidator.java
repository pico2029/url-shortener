package com.example.shortener.link;

import org.springframework.stereotype.Component;

@Component
public class AliasValidator {
    public String normalize(String value) {
        if (value == null) return null;
        String alias = value.strip();
        return alias.isEmpty() ? null : alias;
    }

    public String validate(String value) {
        String alias = normalize(value);
        if (alias != null && !alias.matches("[A-Za-z0-9_-]{3,32}")) {
            throw new InvalidAliasException();
        }
        return alias;
    }
}
