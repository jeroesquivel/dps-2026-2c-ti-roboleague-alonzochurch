package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.Objects;

public record PenaltyDefinition(PenaltyCode code, String description, PointsAmount deduction) {

    public PenaltyDefinition {
        Objects.requireNonNull(code, "penalty code is required");
        Objects.requireNonNull(deduction, "penalty deduction is required");
        if (description == null || description.isBlank()) {
            throw new InvalidValueException("penalty " + code.value() + " requires a description");
        }
    }
}
