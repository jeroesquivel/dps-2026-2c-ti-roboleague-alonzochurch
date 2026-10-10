package com.dps.roboleague.domain.scoring.rule;

import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.scoring.BonusPoints;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.ScoringRuleCode;
import com.dps.roboleague.domain.shared.Points;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ThresholdBonusRule(MetricKey metric, Comparison comparison, MetricValue threshold, BonusPoints bonus)
        implements ScoringRule {

    public static final ScoringRuleCode CODE = ScoringRuleCode.of("BONUS");

    public enum Comparison {

        AT_LEAST("at least") {
            @Override
            public boolean isMetBy(BigDecimal measured, MetricValue threshold) {
                return measured.compareTo(threshold.amount()) >= 0;
            }
        },
        AT_MOST("at most") {
            @Override
            public boolean isMetBy(BigDecimal measured, MetricValue threshold) {
                return measured.compareTo(threshold.amount()) <= 0;
            }
        };

        private final String label;

        Comparison(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public abstract boolean isMetBy(BigDecimal measured, MetricValue threshold);
    }

    public ThresholdBonusRule {
        Objects.requireNonNull(metric, "metric is required");
        Objects.requireNonNull(comparison, "comparison is required");
        Objects.requireNonNull(threshold, "threshold is required");
        Objects.requireNonNull(bonus, "bonus is required");
    }

    @Override
    public List<ScoreContribution> apply(ScoringContext context) {
        return context.measurements().amountOf(metric)
                .map(this::contributionFor)
                .orElseGet(() -> List.of(ScoreContribution.bonus(CODE,
                        "no measurement recorded for %s: bonus not granted".formatted(metric.value()),
                        Points.ZERO)));
    }

    @Override
    public Set<MetricKey> referencedMetrics() {
        return Set.of(metric);
    }

    private List<ScoreContribution> contributionFor(BigDecimal measured) {
        boolean granted = comparison.isMetBy(measured, threshold);
        String explanation = "%s %s %s %s: bonus %s".formatted(metric.value(), measured.toPlainString(),
                granted ? "is" : "is not", comparison.label() + " " + threshold,
                granted ? "granted" : "not granted");
        return List.of(ScoreContribution.bonus(CODE, explanation, granted ? bonus.asPoints() : Points.ZERO));
    }
}
