package com.example.shortener.shared;

import com.example.shortener.auth.DuplicateEmailException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the highlighted fields.");
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("fieldErrors", fields);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail malformed() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_JSON", "The JSON request is invalid.");
    }

    @ExceptionHandler(com.example.shortener.auth.InvalidCredentialsException.class)
    public ProblemDetail credentials() {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password.");
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail duplicate() {
        return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "This email is already registered. Use a different address.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail method() {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This method is not allowed.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail mediaType() {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Send the request in JSON format.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected() {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The service is unavailable. Please try again later.");
    }

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
