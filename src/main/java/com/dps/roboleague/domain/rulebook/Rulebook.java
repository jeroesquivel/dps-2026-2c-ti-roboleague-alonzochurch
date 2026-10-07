package com.dps.roboleague.domain.rulebook;

import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.eligibility.EligibilityPolicy;
import com.dps.roboleague.domain.ranking.AttemptAggregation;
import com.dps.roboleague.domain.ranking.TiebreakRule;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.NotFoundException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Rulebook(CompetitionId competitionId, RulebookVersion version, LocalDate publishedOn,
        Map<ChallengeId, ChallengeSpec> challenges, EligibilityPolicy eligibilityPolicy,
        AttemptAggregation attemptAggregation, List<TiebreakRule> tiebreakRules) {

    public Rulebook {
        Objects.requireNonNull(competitionId, "competition id is required");
        Objects.requireNonNull(version, "rulebook version is required");
        Objects.requireNonNull(publishedOn, "publication date is required");
        Objects.requireNonNull(eligibilityPolicy, "eligibility policy is required");
        Objects.requireNonNull(attemptAggregation, "attempt aggregation is required");
        challenges = Map.copyOf(challenges);
        tiebreakRules = List.copyOf(tiebreakRules);
        if (challenges.isEmpty()) {
            throw new InvalidValueException("a rulebook requires at least one challenge");
        }
    }

    public static Rulebook of(CompetitionId competitionId, RulebookVersion version, LocalDate publishedOn,
            List<ChallengeSpec> challenges, EligibilityPolicy eligibilityPolicy, AttemptAggregation attemptAggregation,
            List<TiebreakRule> tiebreakRules) {
        Map<ChallengeId, ChallengeSpec> indexed = new LinkedHashMap<>();
        for (ChallengeSpec challenge : challenges) {
            if (indexed.putIfAbsent(challenge.id(), challenge) != null) {
                throw new InvalidValueException("challenge " + challenge.id().value()
                        + " is defined twice in rulebook " + version);
            }
        }
        return new Rulebook(competitionId, version, publishedOn, indexed, eligibilityPolicy, attemptAggregation,
                tiebreakRules);
    }

    public ChallengeSpec challenge(ChallengeId challengeId) {
        ChallengeSpec spec = challenges.get(challengeId);
        if (spec == null) {
            throw new NotFoundException("challenge " + challengeId.value() + " is not defined in rulebook " + version);
        }
        return spec;
    }
}
