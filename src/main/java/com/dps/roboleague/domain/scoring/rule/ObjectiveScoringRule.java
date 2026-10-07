package com.dps.roboleague.domain.scoring.rule;

import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.ScoringRuleCode;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ObjectiveScoringRule(MetricKey metric, PointsRate pointsPerObjective, int maximumObjectives)
        implements ScoringRule {

    public static final ScoringRuleCode CODE = ScoringRuleCode.of("OBJECTIVES");

    public ObjectiveScoringRule {
        Objects.requireNonNull(metric, "metric is required");
        Objects.requireNonNull(pointsPerObjective, "points per objective are required");
        if (maximumObjectives < 1) {
            throw new InvalidValueException("the maximum number of objectives must be positive");
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

    private List<ScoreContribution> contributionFor(BigDecimal reported) {
        BigDecimal countedObjectives = reported.min(BigDecimal.valueOf(maximumObjectives));
        Points earned = pointsPerObjective.times(countedObjectives);
        String explanation = "%s of %d objectives at %s points each".formatted(countedObjectives.toBigInteger(),
                maximumObjectives, pointsPerObjective);
        return List.of(ScoreContribution.earned(CODE, explanation, earned));
    }
}
