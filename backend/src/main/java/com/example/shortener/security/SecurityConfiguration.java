package com.example.shortener.security;

import com.example.shortener.link.LinkProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(LinkProperties.class)
@Import({SecurityConfig.class, JwtConfig.class})
public class SecurityConfiguration {}
