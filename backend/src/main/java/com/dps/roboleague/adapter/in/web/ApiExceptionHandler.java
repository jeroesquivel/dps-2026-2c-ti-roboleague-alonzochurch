package com.dps.roboleague.adapter.in.web;

import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiExceptionHandler {

    @ExceptionHandler(InvalidValueException.class)
    ResponseEntity<ErrorResponse> invalidValue(InvalidValueException exception) {
        return error(HttpStatus.BAD_REQUEST, exception);
    }

    @ExceptionHandler(RuleViolationException.class)
    ResponseEntity<ErrorResponse> ruleViolation(RuleViolationException exception) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, exception);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> conflict(ConflictException exception) {
        return error(HttpStatus.CONFLICT, exception);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(NotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformedRequest(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "request body is malformed");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, RuntimeException exception) {
        return error(status, exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }

    public record ErrorResponse(int status, String error, String message) {
    }
}
