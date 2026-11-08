package com.dps.roboleague.adapter.in.graphql.error;

import com.dps.roboleague.scheduling.ScheduleConflictException;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.DomainException;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RuleViolationException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.graphql.execution.ErrorType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada categoría de error del dominio se informa con el mismo código que la API REST. */
class DomainErrorTest {

    static Stream<Arguments> domainErrors() {
        return Stream.of(
                Arguments.of(new InvalidValueException("bad"), "INVALID_VALUE", ErrorType.BAD_REQUEST),
                Arguments.of(NotFoundException.of("Team", "TEAM-9"), "NOT_FOUND", ErrorType.NOT_FOUND),
                Arguments.of(new ConflictException("taken"), "CONFLICT", ErrorType.BAD_REQUEST),
                Arguments.of(new ScheduleConflictException(List.of()), "SCHEDULE_CONFLICT", ErrorType.BAD_REQUEST),
                Arguments.of(new RuleViolationException("broken"), "RULE_VIOLATION", ErrorType.BAD_REQUEST),
                Arguments.of(new DomainException("other") { }, "DOMAIN_ERROR", ErrorType.BAD_REQUEST));
    }

    @ParameterizedTest
    @MethodSource("domainErrors")
    void reportsEachCategoryWithItsCodeAndClassification(DomainException exception, String code,
            ErrorType classification) {
        DomainError error = DomainError.of(exception).orElseThrow();

        assertEquals(code, error.code());
        assertEquals(classification, error.classification());
    }

    @Test
    void leavesAnythingThatIsNotADomainErrorToTheDefaultHandling() {
        assertTrue(DomainError.of(new IllegalStateException("boom")).isEmpty());
    }
}
