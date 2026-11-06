package com.dps.roboleague.rules.scoring;

import com.dps.roboleague.shared.InvalidValueException;
import java.util.Objects;

public record PenaltyDefinition(PenaltyCode code, String description, PointsDeducted deduction) {

    public PenaltyDefinition {
        Objects.requireNonNull(code, "penalty code is required");
        Objects.requireNonNull(deduction, "penalty deduction is required");
        if (description == null || description.isBlank()) {
            throw new InvalidValueException("penalty " + code.value() + " requires a description");
        }
    }
}
