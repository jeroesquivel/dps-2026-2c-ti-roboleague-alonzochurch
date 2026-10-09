package com.dps.roboleague.domain.rulebook;

import com.dps.roboleague.domain.appeal.AppealWindow;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.eligibility.EligibilityRequirements;
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
        Map<ChallengeId, ChallengeSpec> challenges, EligibilityRequirements eligibilityRequirements,
        AttemptAggregation attemptAggregation, List<TiebreakRule> tiebreakRules, AppealWindow appealWindow) {

    public Rulebook {
        Objects.requireNonNull(competitionId, "competition id is required");
        Objects.requireNonNull(version, "rulebook version is required");
        Objects.requireNonNull(publishedOn, "publication date is required");
        Objects.requireNonNull(eligibilityRequirements, "eligibility requirements are required");
        Objects.requireNonNull(attemptAggregation, "attempt aggregation is required");
        Objects.requireNonNull(appealWindow, "appeal window is required");
        challenges = Map.copyOf(challenges);
        tiebreakRules = List.copyOf(tiebreakRules);
        if (challenges.isEmpty()) {
            throw new InvalidValueException("a rulebook requires at least one challenge");
        }
    }

    public static Rulebook of(CompetitionId competitionId, RulebookVersion version, LocalDate publishedOn,
            RulebookDraft draft) {
        Map<ChallengeId, ChallengeSpec> indexed = new LinkedHashMap<>();
        draft.challenges().forEach(challenge -> indexed.put(challenge.id(), challenge));
        return new Rulebook(competitionId, version, publishedOn, indexed, draft.eligibilityRequirements(),
                draft.attemptAggregation(), draft.tiebreakRules(), draft.appealWindow());
    }

    public ChallengeSpec challenge(ChallengeId challengeId) {
        ChallengeSpec spec = challenges.get(challengeId);
        if (spec == null) {
            throw new NotFoundException("challenge " + challengeId.value() + " is not defined in rulebook " + version);
        }
        return spec;
    }
}
