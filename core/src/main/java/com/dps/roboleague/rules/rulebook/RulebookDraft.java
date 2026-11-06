package com.dps.roboleague.rules.rulebook;

import com.dps.roboleague.enrollment.eligibility.EligibilityRequirements;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.ranking.AttemptAggregation;
import com.dps.roboleague.rules.ranking.TiebreakRule;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.InvalidValueException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RulebookDraft(List<ChallengeSpec> challenges, EligibilityRequirements eligibilityRequirements,
        AttemptAggregation attemptAggregation, List<TiebreakRule> tiebreakRules, AppealWindow appealWindow) {

    public RulebookDraft {
        Objects.requireNonNull(eligibilityRequirements, "eligibility requirements are required");
        Objects.requireNonNull(attemptAggregation, "attempt aggregation is required");
        Objects.requireNonNull(appealWindow, "appeal window is required");
        challenges = List.copyOf(challenges);
        tiebreakRules = List.copyOf(tiebreakRules);
        if (challenges.isEmpty()) {
            throw new InvalidValueException("a rulebook requires at least one challenge");
        }
        Set<ChallengeId> seen = new HashSet<>();
        challenges.stream()
                .map(ChallengeSpec::id)
                .filter(challengeId -> !seen.add(challengeId))
                .findFirst()
                .ifPresent(repeated -> {
                    throw new InvalidValueException("challenge " + repeated.value() + " is defined twice");
                });
    }
}
