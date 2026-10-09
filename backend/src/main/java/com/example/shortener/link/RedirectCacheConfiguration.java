package com.example.shortener.link;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedirectCacheProperties.class)
public class RedirectCacheConfiguration {
}
