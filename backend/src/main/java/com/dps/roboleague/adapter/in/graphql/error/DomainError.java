package com.dps.roboleague.adapter.in.graphql.error;

import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.DomainException;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RuleViolationException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.graphql.execution.ErrorType;

/**
 * Cómo se informa en GraphQL cada categoría de error del dominio: la clasificación estándar de Spring for GraphQL y
 * el código de la API, que es el nombre de la constante y coincide con el que usa REST. Las constantes van de la más
 * específica a la más general: un error se informa con la primera cuya categoría lo incluye.
 */
enum DomainError {

    INVALID_VALUE(InvalidValueException.class, ErrorType.BAD_REQUEST),
    NOT_FOUND(NotFoundException.class, ErrorType.NOT_FOUND),
    CONFLICT(ConflictException.class, ErrorType.BAD_REQUEST),
    RULE_VIOLATION(RuleViolationException.class, ErrorType.BAD_REQUEST),
    DOMAIN_ERROR(DomainException.class, ErrorType.BAD_REQUEST);

    private final Class<? extends DomainException> category;
    private final ErrorType classification;

    DomainError(Class<? extends DomainException> category, ErrorType classification) {
        this.category = category;
        this.classification = classification;
    }

    /** La categoría de un error del dominio; vacío si no es un error del dominio. */
    static Optional<DomainError> of(Throwable exception) {
        return Arrays.stream(values()).filter(error -> error.category.isInstance(exception)).findFirst();
    }

    ErrorType classification() {
        return classification;
    }

    String code() {
        return name();
    }
}
