package com.example.shortener.security;

import com.example.shortener.shared.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        String id = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(id, Map.of(id, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.ignoringRequestMatchers(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/register"),
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/login"),
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/links")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/r/{shortCode}").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/r/{shortCode}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/links").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(org.springframework.security.config.Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), ApiExceptionHandler.problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Your session is invalid. Please log in again."));
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), ApiExceptionHandler.problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "È necessario accedere."));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), ApiExceptionHandler.problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "This request is not allowed."));
                        }))
                .build();
    }
}
