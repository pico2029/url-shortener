package com.example.shortener.link;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedirectCacheProperties.class)
public class RedirectCacheConfiguration {
    @Bean
    Clock redirectClock() {
        return Clock.systemUTC();
    }
}
