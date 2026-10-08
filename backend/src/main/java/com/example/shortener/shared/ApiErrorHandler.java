package com.example.shortener.shared;

import com.example.shortener.link.InvalidUrlException;
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

    @ExceptionHandler({InvalidPageException.class,
            MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError pagination() {
        return new ApiError("INVALID_REQUEST", "Utilizza page >= 0 e size tra 1 e 100, senza altri parametri.", null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError unauthorized() {
        return new ApiError("UNAUTHORIZED", "La sessione non è valida. Accedi nuovamente.", null);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError persistence() { return unavailable(); }

    @ExceptionHandler(LinkUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError unavailable() {
        return new ApiError("SERVICE_UNAVAILABLE", "Il servizio dei link non è disponibile. Riprova più tardi.", null);
    }
}
