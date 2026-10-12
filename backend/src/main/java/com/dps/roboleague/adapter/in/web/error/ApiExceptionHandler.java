package com.dps.roboleague.adapter.in.web.error;

import com.dps.roboleague.domain.schedule.ScheduleConflictException;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.DomainException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce la jerarquía de excepciones del dominio al contrato HTTP. Los códigos de estado pertenecen
 * a este adaptador; el dominio sólo conoce sus propias excepciones.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidValueException.class)
    public ResponseEntity<ErrorResponse> invalidValue(InvalidValueException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_VALUE", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformedRequest(HttpMessageNotReadableException exception) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "the request body is missing or malformed");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ScheduleConflictException.class)
    public ResponseEntity<ScheduleConflictResponse> scheduleConflict(ScheduleConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ScheduleConflictResponse("SCHEDULE_CONFLICT",
                exception.getMessage(),
                exception.conflicts().stream()
                        .map(conflict -> new ScheduleConflictResponse.ConflictDetail(conflict.type().name(),
                                conflict.detail()))
                        .toList()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException exception) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(RuleViolationException.class)
    public ResponseEntity<ErrorResponse> ruleViolation(RuleViolationException exception) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "RULE_VIOLATION", exception.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> otherDomainError(DomainException exception) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_ERROR", exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
