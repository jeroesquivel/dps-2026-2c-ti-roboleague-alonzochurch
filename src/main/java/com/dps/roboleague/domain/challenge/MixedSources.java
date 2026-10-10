package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record MixedSources() {

    public static final ResultSource INCIDENT_SOURCE = ResultSource.JUDGES;

    void requireRulesOfEverySource(List<MetricDefinition> metrics, List<ScoringRule> rules, String challenge) {
        Set<ResultSource> covered = rules.stream()
                .map(rule -> sourceOf(rule, metrics, challenge))
                .collect(Collectors.toUnmodifiableSet());
        Arrays.stream(ResultSource.values())
                .filter(source -> !covered.contains(source))
                .findFirst()
                .ifPresent(missing -> {
                    throw new InvalidValueException("mixed challenge " + challenge
                            + " requires at least one scoring rule of the " + missing + " source");
                });
    }

    ResultSource sourceOf(ScoringRule rule, List<MetricDefinition> metrics, String challenge) {
        Set<ResultSource> read = metrics.stream()
                .filter(definition -> rule.referencedMetrics().contains(definition.key()))
                .map(definition -> definition.kind().source())
                .collect(Collectors.toUnmodifiableSet());
        if (read.size() > 1) {
            throw new InvalidValueException("a scoring rule of mixed challenge " + challenge + " reads "
                    + rule.referencedMetrics().stream()
                            .map(MetricKey::value)
                            .sorted()
                            .collect(Collectors.joining(", "))
                    + ", which arrive from different sources");
        }
        return read.stream().findFirst().orElse(ResultSource.AUTOMATIC);
    }

    List<MetricDefinition> measuredAmong(List<MetricDefinition> metrics) {
        return metrics.stream().filter(definition -> definition.isSuppliedBy(ResultSource.AUTOMATIC)).toList();
    }
}
