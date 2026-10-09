package com.example.shortener.link.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortener")
public record LinkProperties(URI publicBaseUrl) {
    public LinkProperties {
        if (publicBaseUrl == null || publicBaseUrl.getHost() == null
                || !("http".equalsIgnoreCase(publicBaseUrl.getScheme())
                || "https".equalsIgnoreCase(publicBaseUrl.getScheme()))
                || publicBaseUrl.getRawUserInfo() != null || publicBaseUrl.getRawQuery() != null
                || publicBaseUrl.getRawFragment() != null || publicBaseUrl.getPort() > 65535
                || publicBaseUrl.getPort() == 0
                || !(publicBaseUrl.getPath().isEmpty() || "/".equals(publicBaseUrl.getPath()))) {
            throw new IllegalArgumentException("shortener.public-base-url must be an HTTP/HTTPS origin");
        }
    }

    public String shortUrl(String code) {
        return publicBaseUrl.toString().replaceAll("/$", "") + "/r/" + code;
    }
}
