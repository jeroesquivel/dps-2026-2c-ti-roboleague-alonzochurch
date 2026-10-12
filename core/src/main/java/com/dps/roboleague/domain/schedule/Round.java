package com.dps.roboleague.domain.schedule;

import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.TeamRegistration;
import java.util.Objects;

public final class Round {

    private final RoundId id;
    private final CompetitionId competitionId;
    private final CategoryId categoryId;
    private final ChallengeId challengeId;
    private final RoundOrdinal ordinal;
    private final RulebookVersion rulebookVersion;
    private final Heats heats;

    public Round(RoundId id, CompetitionId competitionId, CategoryId categoryId, ChallengeId challengeId,
            RoundOrdinal ordinal, RulebookVersion rulebookVersion) {
        this(id, competitionId, categoryId, challengeId, ordinal, rulebookVersion, Heats.none());
    }

    private Round(RoundId id, CompetitionId competitionId, CategoryId categoryId, ChallengeId challengeId,
            RoundOrdinal ordinal, RulebookVersion rulebookVersion, Heats heats) {
        this.id = Objects.requireNonNull(id, "round id is required");
        this.competitionId = Objects.requireNonNull(competitionId, "competition id is required");
        this.categoryId = Objects.requireNonNull(categoryId, "category id is required");
        this.challengeId = Objects.requireNonNull(challengeId, "challenge id is required");
        this.ordinal = Objects.requireNonNull(ordinal, "round ordinal is required");
        this.rulebookVersion = Objects.requireNonNull(rulebookVersion, "rulebook version is required");
        this.heats = heats;
    }

    public Round schedule(Heat heat, TeamRegistration team) {
        if (!heat.roundId().equals(id)) {
            throw new RuleViolationException(
                    "heat " + heat.id().value() + " does not belong to round " + id.value());
        }
        if (!heat.teamId().equals(team.id())) {
            throw new RuleViolationException("heat " + heat.id().value() + " is not assigned to team " + team.name());
        }
        team.requireAcceptedIn(competitionId, categoryId);
        return new Round(id, competitionId, categoryId, challengeId, ordinal, rulebookVersion, heats.with(heat));
    }

    public Heat heatFor(TeamId teamId) {
        return heats.heatFor(teamId).orElseThrow(() -> new RuleViolationException(
                "team " + teamId.value() + " has no heat in round " + id.value()));
    }

    public RoundId id() {
        return id;
    }

    public CompetitionId competitionId() {
        return competitionId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public ChallengeId challengeId() {
        return challengeId;
    }

    public RoundOrdinal ordinal() {
        return ordinal;
    }

    public RulebookVersion rulebookVersion() {
        return rulebookVersion;
    }

    public Heats heats() {
        return heats;
    }
}
