package com.dps.roboleague.rules.scoring;

import com.dps.roboleague.shared.InvalidValueException;
import java.util.Locale;

public record ScoringRuleCode(String value) {

    public ScoringRuleCode {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("a scoring rule code requires a non blank value");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
    }

    public static ScoringRuleCode of(String value) {
        return new ScoringRuleCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
