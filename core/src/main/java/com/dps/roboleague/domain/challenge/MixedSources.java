package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.rule.PenaltyScoringRule;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record MixedSources() implements CaptureMode {

    public static final ResultSource INCIDENT_SOURCE = ResultSource.JUDGES;

    @Override
    public void requireSingleCapture(String challenge) {
        throw new RuleViolationException("challenge " + challenge + " is mixed: its automatic measurements and"
                + " its panel evaluations are registered separately");
    }

    @Override
    public void requireMixedSources(String challenge) {
        // es el modo esperado
    }

    @Override
    public void requireRulesOfEverySource(List<MetricDefinition> metrics, List<ScoringRule> rules, String challenge) {
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

    @Override
    public Optional<SourcedScore> scoreBySource(ChallengeSpec challenge, ScoringContext context) {
        Map<ResultSource, List<ScoreContribution>> bySource = new EnumMap<>(ResultSource.class);
        Arrays.stream(ResultSource.values()).forEach(source -> bySource.put(source, new ArrayList<>()));
        challenge.scoringRules().forEach(rule -> bySource
                .get(sourceOf(rule, challenge.metrics(), challenge.name()))
                .addAll(rule.apply(context)));
        bySource.get(INCIDENT_SOURCE).addAll(PenaltyScoringRule.of(challenge.penalties()).apply(context));
        return Optional.of(new SourcedScore(bySource.entrySet().stream()
                .map(group -> new SourceBreakdown(group.getKey(), new ScoreBreakdown(group.getValue())))
                .toList(), challenge.bonusTrimOf(context)));
    }

    @Override
    public List<MetricDefinition> measuredAmong(List<MetricDefinition> metrics) {
        return metrics.stream().filter(definition -> definition.isSuppliedBy(ResultSource.AUTOMATIC)).toList();
    }
}
