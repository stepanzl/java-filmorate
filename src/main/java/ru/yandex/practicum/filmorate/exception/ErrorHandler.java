package ru.yandex.practicum.filmorate.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(final ValidationException e) {
        log.error("Validation error: {}", e.getMessage());
        return Map.of(
                "error", "Validation error",
                "message", e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(final NotFoundException e) {
        log.error("Not found: {}", e.getMessage());
        return Map.of(
                "error", "Not found",
                "message", e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleOther(final Exception e) {
        log.error("Internal server error", e);
        return Map.of(
                "error", "Internal server error",
                "message", e.getMessage() == null ? "Unexpected error" : e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleMethodArgumentNotValid(final MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        log.error("Validation error: {}", errorMessage);
        return Map.of(
                "error", "Validation error",
                "message", errorMessage
        );
    }
}
