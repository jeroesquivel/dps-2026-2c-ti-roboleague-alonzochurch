package com.dps.roboleague.rules.ranking.rule;

import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.ranking.TeamScoreSummary;
import com.dps.roboleague.rules.ranking.TiebreakRule;
import java.util.Objects;
import java.util.Optional;

public final class FastestMetricTiebreak implements TiebreakRule {

    public static final String CODE = "FASTEST_METRIC";

    private final MetricKey metric;

    public FastestMetricTiebreak(MetricKey metric) {
        this.metric = Objects.requireNonNull(metric, "metric is required");
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String description() {
        return "the team with the lowest value of " + metric.value() + " ranks first";
    }

    @Override
    public int compare(TeamScoreSummary left, TeamScoreSummary right) {
        Optional<MetricValue> leftBest = left.lowestMeasurement(metric);
        Optional<MetricValue> rightBest = right.lowestMeasurement(metric);
        if (leftBest.isEmpty() || rightBest.isEmpty()) {
            return Boolean.compare(leftBest.isEmpty(), rightBest.isEmpty());
        }
        return leftBest.get().compareTo(rightBest.get());
    }
}
