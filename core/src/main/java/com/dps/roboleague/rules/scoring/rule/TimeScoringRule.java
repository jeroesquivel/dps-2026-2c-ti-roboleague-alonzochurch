package com.dps.roboleague.rules.scoring.rule;

import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.scoring.PointsCap;
import com.dps.roboleague.rules.scoring.PointsRate;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.rules.scoring.ScoringRule;
import com.dps.roboleague.rules.scoring.ScoringRuleCode;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.Points;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TimeScoringRule(MetricKey metric, Duration reference, PointsRate pointsPerSecondSaved,
        PointsCap maximumPoints) implements ScoringRule {

    public static final ScoringRuleCode CODE = ScoringRuleCode.of("TIME");

    public TimeScoringRule {
        Objects.requireNonNull(metric, "metric is required");
        Objects.requireNonNull(reference, "reference time is required");
        Objects.requireNonNull(pointsPerSecondSaved, "points per second saved are required");
        Objects.requireNonNull(maximumPoints, "maximum points are required");
        if (reference.isZero() || reference.isNegative()) {
            throw new InvalidValueException("the reference time must be positive");
        }
    }

    @Override
    public List<ScoreContribution> apply(ScoringContext context) {
        return context.measurements().amountOf(metric)
                .map(this::contributionFor)
                .orElseGet(() -> List.of(ScoreContribution.earned(CODE,
                        "no measurement recorded for " + metric.value(), Points.ZERO)));
    }

    @Override
    public Set<MetricKey> referencedMetrics() {
        return Set.of(metric);
    }

    private List<ScoreContribution> contributionFor(BigDecimal elapsed) {
        BigDecimal referenceSeconds = BigDecimal.valueOf(reference.toMillis()).movePointLeft(3);
        BigDecimal saved = referenceSeconds.subtract(elapsed);
        Points earned = saved.signum() <= 0
                ? Points.ZERO
                : maximumPoints.limit(pointsPerSecondSaved.times(saved));
        String explanation = "%s s against a reference of %s s".formatted(elapsed.toPlainString(),
                referenceSeconds.toPlainString());
        return List.of(ScoreContribution.earned(CODE, explanation, earned));
    }
}
