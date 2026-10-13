package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.shared.Points;
import java.util.Objects;

public record BonusCap(PointsCap maximum) {

    public static final ScoringRuleCode CODE = ScoringRuleCode.of("BONUS_CAP");

    public BonusCap {
        Objects.requireNonNull(maximum, "bonus cap maximum is required");
    }

    public static BonusCap of(long maximum) {
        return new BonusCap(PointsCap.of(maximum));
    }

    public BonusCapOutcome outcomeOf(ScoreBreakdown breakdown) {
        Points obtained = breakdown.totalOf(ContributionKind.BONUS);
        Points applied = maximum.limit(obtained);
        return new BonusCapOutcome(obtained, maximum.value(), obtained.plus(applied.negated()));
    }

    public ScoreContribution trim(ScoreBreakdown breakdown) {
        BonusCapOutcome outcome = outcomeOf(breakdown);
        return ScoreContribution.bonus(CODE, explanationOf(outcome), outcome.trimmed().negated());
    }

    private String explanationOf(BonusCapOutcome outcome) {
        boolean exceeded = outcome.trimmed().compareTo(Points.ZERO) > 0;
        return exceeded
                ? "bonuses obtained %s exceed the cap of %s: %s trimmed".formatted(outcome.obtained(), maximum,
                        outcome.trimmed())
                : "bonuses obtained %s are within the cap of %s: nothing trimmed".formatted(outcome.obtained(),
                        maximum);
    }
}
