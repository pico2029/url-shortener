package com.example.shortener.shared;

import com.example.shortener.link.InvalidUrlException;
import com.example.shortener.link.LinkUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrorHandler {
    public record ApiError(String code, String message, String field) {}

    @ExceptionHandler(InvalidUrlException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidUrl(InvalidUrlException exception) {
        return new ApiError("INVALID_URL", exception.getMessage(), "originalUrl");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError malformedJson() {
        return new ApiError("INVALID_REQUEST", "La richiesta non è valida.", null);
    }

    @ExceptionHandler(LinkUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError unavailable() {
        return new ApiError("SERVICE_UNAVAILABLE", "Impossibile creare il link. Riprova più tardi.", null);
    }
}
