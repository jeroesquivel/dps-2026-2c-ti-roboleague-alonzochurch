package com.dps.roboleague.rules.scoring;

import com.dps.roboleague.shared.InvalidValueException;
import java.util.Objects;

public record IncidentReport(PenaltyCode code, int occurrences) {

    public IncidentReport {
        Objects.requireNonNull(code, "penalty code is required");
        if (occurrences < 1) {
            throw new InvalidValueException("an incident requires at least one occurrence");
        }
    }

    public static IncidentReport once(PenaltyCode code) {
        return new IncidentReport(code, 1);
    }
}
