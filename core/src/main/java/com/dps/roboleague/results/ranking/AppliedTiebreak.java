package com.dps.roboleague.results.ranking;

import com.dps.roboleague.rules.ranking.TiebreakRule;
import com.dps.roboleague.shared.InvalidValueException;

public record AppliedTiebreak(String code, String description) {

    public AppliedTiebreak {
        if (code == null || code.isBlank() || description == null || description.isBlank()) {
            throw new InvalidValueException("an applied tiebreak requires a code and a description");
        }
    }

    public static AppliedTiebreak of(TiebreakRule rule) {
        return new AppliedTiebreak(rule.code(), rule.description());
    }
}
