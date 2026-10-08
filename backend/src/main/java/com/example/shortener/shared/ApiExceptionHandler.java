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
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Controlla i campi indicati.");
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("fieldErrors", fields);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail malformed() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_JSON", "La richiesta JSON non è valida.");
    }

    @ExceptionHandler(com.example.shortener.auth.InvalidCredentialsException.class)
    public ProblemDetail credentials() {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email o password non corrette.");
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail duplicate() {
        return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Questa email è già registrata. Usa un altro indirizzo.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail method() {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Metodo non consentito.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail mediaType() {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Invia la richiesta in formato JSON.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected() {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Il servizio non è disponibile. Riprova più tardi.");
    }

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
