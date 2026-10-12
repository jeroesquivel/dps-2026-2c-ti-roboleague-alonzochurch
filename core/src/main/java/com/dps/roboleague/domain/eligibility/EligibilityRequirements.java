package com.dps.roboleague.domain.eligibility;

import java.util.List;

public record EligibilityRequirements(List<EligibilityRule> rules) {

    public EligibilityRequirements {
        rules = List.copyOf(rules);
    }

    public static EligibilityRequirements of(EligibilityRule... rules) {
        return new EligibilityRequirements(List.of(rules));
    }

    public EligibilityVerdict verdictFor(EligibilityRequest request) {
        return new EligibilityVerdict(rules.stream().flatMap(rule -> rule.evaluate(request).stream()).toList());
    }
}
