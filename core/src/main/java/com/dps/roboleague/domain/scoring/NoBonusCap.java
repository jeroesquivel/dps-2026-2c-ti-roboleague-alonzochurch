package com.dps.roboleague.domain.scoring;

import java.util.List;
import java.util.Optional;

/** Las bonificaciones no tienen tope. */
public record NoBonusCap() implements BonusPolicy {

    @Override
    public List<ScoreContribution> trimContributions(ScoreBreakdown breakdown) {
        return List.of();
    }

    @Override
    public Optional<BonusCapOutcome> capOutcomeOf(ScoreBreakdown breakdown) {
        return Optional.empty();
    }
}
