package com.dps.roboleague.domain.eligibility;

import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.Objects;

public record EligibilityViolation(EligibilityRuleCode ruleCode, String reason) {

    public EligibilityViolation {
        Objects.requireNonNull(ruleCode, "rule code is required");
        if (reason == null || reason.isBlank()) {
            throw new InvalidValueException("an eligibility violation requires a reason");
        }
    }

    @Override
    public String toString() {
        return ruleCode + ": " + reason;
    }
}
