package com.example.shortener.link;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;

@Component
public class UrlValidator {
    public String validate(String value) {
        if (value == null) throw new InvalidUrlException();
        String url = value.strip();
        if (url.isEmpty() || url.length() > 8192
                || url.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new InvalidUrlException();
        }
        try {
            URI uri = new URI(url).parseServerAuthority();
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getPort() > 65535 || uri.getPort() == 0
                    || uri.getRawAuthority().endsWith(":")) {
                throw new InvalidUrlException();
            }
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException();
        }
        return url;
    }
}
