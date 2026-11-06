package com.dps.roboleague.rules.rulebook;

import com.dps.roboleague.enrollment.eligibility.EligibilityRequirements;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.ranking.AttemptAggregation;
import com.dps.roboleague.rules.ranking.RoundPolicy;
import com.dps.roboleague.rules.ranking.RoundSelection;
import com.dps.roboleague.rules.ranking.TiebreakRule;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RulebookVersion;
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

    /** La política con que este reglamento cuenta las rondas de un desafío. */
    public RoundPolicy roundPolicyOf(ChallengeId challengeId) {
        ChallengeSpec spec = challenges.get(challengeId);
        RoundSelection selection = spec == null ? RoundSelection.all() : spec.roundSelection();
        return new RoundPolicy(version, selection, attemptAggregation);
    }
}
