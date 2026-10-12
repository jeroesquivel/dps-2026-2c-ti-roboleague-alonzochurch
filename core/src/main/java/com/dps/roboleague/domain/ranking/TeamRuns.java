package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RunId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record TeamRuns(List<ScoredRun> runs, AttemptAggregation aggregation) {

    public TeamRuns {
        Objects.requireNonNull(aggregation, "attempt aggregation is required");
        runs = List.copyOf(runs);
        Set<RunId> seen = new HashSet<>();
        runs.stream()
                .map(ScoredRun::runId)
                .filter(runId -> !seen.add(runId))
                .findFirst()
                .ifPresent(repeated -> {
                    throw new InvalidValueException("run " + repeated.value() + " is counted twice for the team");
                });
    }

    public Points aggregatedPoints() {
        return aggregation.aggregate(runs);
    }

    public ScoreSubtotal subtotal() {
        return ScoreSubtotal.of(aggregation.code(), aggregation.description(), aggregatedPoints());
    }

    public Optional<Points> bestRunPoints() {
        return runs.stream().map(ScoredRun::total).max(Comparator.naturalOrder());
    }

    public Points penaltyPoints() {
        return runs.stream()
                .map(run -> run.breakdown().totalOf(ContributionKind.PENALTY))
                .reduce(Points.ZERO, Points::plus);
    }

    public Optional<MetricValue> lowestMeasurement(MetricKey key) {
        return runs.stream()
                .map(run -> run.measurements().find(key))
                .flatMap(Optional::stream)
                .min(Comparator.comparing(MetricValue::amount));
    }
}
