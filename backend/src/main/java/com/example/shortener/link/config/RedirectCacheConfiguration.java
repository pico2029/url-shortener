package com.example.shortener.link.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedirectCacheProperties.class)
public class RedirectCacheConfiguration {
}
