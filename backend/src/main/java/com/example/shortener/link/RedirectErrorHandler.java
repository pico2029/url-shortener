package com.example.shortener.link;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RedirectController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RedirectErrorHandler {
    private final String notFound;
    private final String unavailable;

    public RedirectErrorHandler() throws IOException {
        notFound = new ClassPathResource("redirect-pages/not-found.html").getContentAsString(StandardCharsets.UTF_8);
        unavailable = new ClassPathResource("redirect-pages/unavailable.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<String> notFound(HttpServletRequest request) {
        return page(HttpStatus.NOT_FOUND, notFound, request);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<String> unavailable(HttpServletRequest request) {
        return page(HttpStatus.SERVICE_UNAVAILABLE, unavailable, request);
    }

    private ResponseEntity<String> page(HttpStatus status, String html, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .contentLength(html.getBytes(StandardCharsets.UTF_8).length)
                .body("HEAD".equals(request.getMethod()) ? null : html);
    }
}
