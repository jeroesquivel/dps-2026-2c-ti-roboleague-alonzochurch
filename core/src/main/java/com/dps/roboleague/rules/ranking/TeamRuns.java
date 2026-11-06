package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.scoring.ContributionKind;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
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

    /** Subtotal de los intentos del equipo, indicando bajo qué versión del reglamento se agregaron. */
    public ScoreSubtotal subtotal(RulebookVersion version) {
        return ScoreSubtotal.of(aggregation.code(), aggregation.description() + " (rulebook " + version + ")",
                aggregatedPoints());
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
