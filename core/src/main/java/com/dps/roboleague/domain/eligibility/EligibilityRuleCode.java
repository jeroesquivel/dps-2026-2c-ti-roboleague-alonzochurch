package com.dps.roboleague.domain.eligibility;

import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.Locale;

public record EligibilityRuleCode(String value) {

    public EligibilityRuleCode {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("an eligibility rule code requires a non blank value");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
    }

    public static EligibilityRuleCode of(String value) {
        return new EligibilityRuleCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
