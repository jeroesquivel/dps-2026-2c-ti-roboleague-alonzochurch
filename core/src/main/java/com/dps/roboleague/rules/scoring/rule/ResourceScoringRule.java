package com.dps.roboleague.rules.scoring.rule;

import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.scoring.PointsRate;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.rules.scoring.ScoringRule;
import com.dps.roboleague.rules.scoring.ScoringRuleCode;
import com.dps.roboleague.shared.Points;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ResourceScoringRule(MetricKey metric, MetricValue allowance, PointsRate pointsPerUnitOver)
        implements ScoringRule {

    public static final ScoringRuleCode CODE = ScoringRuleCode.of("RESOURCE");

    public ResourceScoringRule {
        Objects.requireNonNull(metric, "metric is required");
        Objects.requireNonNull(allowance, "allowance is required");
        Objects.requireNonNull(pointsPerUnitOver, "points per unit over the allowance are required");
    }

    @Override
    public List<ScoreContribution> apply(ScoringContext context) {
        return context.measurements().amountOf(metric)
                .map(this::contributionFor)
                .orElseGet(() -> List.of(ScoreContribution.penalty(CODE,
                        "no measurement recorded for " + metric.value(), Points.ZERO)));
    }

    @Override
    public Set<MetricKey> referencedMetrics() {
        return Set.of(metric);
    }

    private List<ScoreContribution> contributionFor(BigDecimal consumed) {
        BigDecimal excess = consumed.subtract(allowance.amount());
        if (excess.signum() <= 0) {
            return List.of(ScoreContribution.penalty(CODE,
                    "consumption %s within the allowance of %s".formatted(consumed.toPlainString(), allowance),
                    Points.ZERO));
        }
        Points deduction = pointsPerUnitOver.times(excess).negated();
        String explanation = "consumption %s exceeds the allowance of %s by %s"
                .formatted(consumed.toPlainString(), allowance, excess.stripTrailingZeros().toPlainString());
        return List.of(ScoreContribution.penalty(CODE, explanation, deduction));
    }
}
