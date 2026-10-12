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

    public ScoreContribution trim(ScoreBreakdown breakdown) {
        Points obtained = breakdown.totalOf(ContributionKind.BONUS);
        Points applied = maximum.limit(obtained);
        Points trimmed = obtained.plus(applied.negated());
        return ScoreContribution.bonus(CODE, explanationOf(obtained, trimmed), trimmed.negated());
    }

    private String explanationOf(Points obtained, Points trimmed) {
        boolean exceeded = trimmed.compareTo(Points.ZERO) > 0;
        return exceeded
                ? "bonuses obtained %s exceed the cap of %s: %s trimmed".formatted(obtained, maximum, trimmed)
                : "bonuses obtained %s are within the cap of %s: nothing trimmed".formatted(obtained, maximum);
    }
}
