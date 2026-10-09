package com.example.shortener.shared;

import com.example.shortener.link.InvalidUrlException;
import com.example.shortener.link.LinkNotFoundException;
import com.example.shortener.link.InvalidPageException;
import com.example.shortener.auth.InvalidCredentialsException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.example.shortener.link.LinkController;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import com.example.shortener.link.LinkUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = LinkController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiErrorHandler {
    public record ApiError(String code, String message, String field) {}

    @ExceptionHandler(com.example.shortener.link.InvalidAliasException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidAlias(com.example.shortener.link.InvalidAliasException exception) {
        return new ApiError("INVALID_ALIAS", exception.getMessage(), "alias");
    }

    @ExceptionHandler(com.example.shortener.link.AliasAlreadyTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError aliasTaken(com.example.shortener.link.AliasAlreadyTakenException exception) {
        return new ApiError("ALIAS_ALREADY_TAKEN", exception.getMessage(), "alias");
    }

    @ExceptionHandler(com.example.shortener.link.AliasAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError aliasAuthentication(com.example.shortener.link.AliasAuthenticationException exception) {
        return new ApiError("UNAUTHORIZED", exception.getMessage(), null);
    }

    @ExceptionHandler(com.example.shortener.link.InvalidExpirationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidExpiration(com.example.shortener.link.InvalidExpirationException exception) {
        return new ApiError("INVALID_EXPIRATION", exception.getMessage(), "expiresAt");
    }

    @ExceptionHandler(com.example.shortener.link.ExpirationAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError expirationAuthentication(com.example.shortener.link.ExpirationAuthenticationException exception) {
        return new ApiError("UNAUTHORIZED", exception.getMessage(), null);
    }

    @ExceptionHandler(LinkNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound() {
        return new ApiError("LINK_NOT_FOUND", "The link is not available.", null);
    }

    @ExceptionHandler(InvalidUrlException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidUrl(InvalidUrlException exception) {
        return new ApiError("INVALID_URL", exception.getMessage(), "originalUrl");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError malformedJson(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof com.fasterxml.jackson.databind.JsonMappingException mapping
                && mapping.getPath().stream().anyMatch(ref -> "expiresAt".equals(ref.getFieldName()))) {
            return new ApiError("INVALID_REQUEST", "Use an ISO-8601 date and time with Z or an offset.", "expiresAt");
        }
        return new ApiError("INVALID_REQUEST", "The request is invalid.", null);
    }

    @ExceptionHandler({InvalidPageException.class,
            MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError pagination() {
        return new ApiError("INVALID_REQUEST", "Use page >= 0 and size between 1 and 100, without other parameters.", null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError unauthorized() {
        return new ApiError("UNAUTHORIZED", "Your session is invalid. Please log in again.", null);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError persistence() { return unavailable(); }

    @ExceptionHandler(LinkUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError unavailable() {
        return new ApiError("SERVICE_UNAVAILABLE", "The link service is unavailable. Please try again later.", null);
    }
}
