package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.scoring.PenaltyDefinition;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.rule.PenaltyScoringRule;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ChallengeSpec(ChallengeId id, String name, List<MetricDefinition> metrics,
        List<ScoringRule> scoringRules, List<PenaltyDefinition> penalties, AttemptLimit maximumAttempts,
        Optional<BestRounds> bestRounds) {

    public ChallengeSpec {
        Objects.requireNonNull(id, "challenge id is required");
        Objects.requireNonNull(maximumAttempts, "maximum attempts are required");
        Objects.requireNonNull(bestRounds, "best rounds configuration is required, even if empty");
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("challenge requires a name");
        }
        metrics = List.copyOf(metrics);
        scoringRules = List.copyOf(scoringRules);
        penalties = List.copyOf(penalties);
        if (metrics.isEmpty()) {
            throw new InvalidValueException("challenge " + name + " requires at least one metric");
        }
        if (scoringRules.isEmpty()) {
            throw new InvalidValueException("challenge " + name + " requires at least one scoring rule");
        }
        requireUnique(metrics, MetricDefinition::key, MetricKey::value, "metric", name);
        requireUnique(penalties, PenaltyDefinition::code, PenaltyCode::value, "penalty", name);
        requireDefinedMetrics(metrics, scoringRules, name);
    }

    public ChallengeSpec(ChallengeId id, String name, List<MetricDefinition> metrics, List<ScoringRule> scoringRules,
            List<PenaltyDefinition> penalties, AttemptLimit maximumAttempts) {
        this(id, name, metrics, scoringRules, penalties, maximumAttempts, Optional.empty());
    }

    public void validate(MeasurementSet measurements) {
        measurements.keys().stream()
                .filter(key -> definitionOf(key).isEmpty())
                .findFirst()
                .ifPresent(unknown -> {
                    throw new RuleViolationException(
                            "metric " + unknown.value() + " is not defined for challenge " + name);
                });
        metrics.forEach(definition -> validateAgainst(definition, measurements));
    }

    public void validateIncidents(List<IncidentReport> incidents) {
        Set<PenaltyCode> defined = penalties.stream()
                .map(PenaltyDefinition::code)
                .collect(Collectors.toUnmodifiableSet());
        incidents.stream()
                .map(IncidentReport::code)
                .filter(code -> !defined.contains(code))
                .findFirst()
                .ifPresent(unknown -> {
                    throw new RuleViolationException(
                            "penalty " + unknown.value() + " is not defined for challenge " + name);
                });
    }

    public void validateEvaluations(JudgeEvaluations evaluations, Set<JudgeId> panel) {
        evaluations.criteria().stream()
                .filter(criterion -> definitionOf(criterion)
                        .filter(definition -> definition.kind() == MetricKind.JUDGE_CRITERION)
                        .isEmpty())
                .findFirst()
                .ifPresent(unknown -> {
                    throw new RuleViolationException(
                            "criterion " + unknown.value() + " is not a judge criterion of challenge " + name);
                });
        evaluations.requireEvaluatorsWithin(panel);
    }

    public ScoreBreakdown score(ScoringContext context) {
        return new ScoreBreakdown(Stream.concat(
                scoringRules.stream().flatMap(rule -> rule.apply(context).stream()),
                PenaltyScoringRule.of(penalties).apply(context).stream()).toList());
    }

    public void requireAttemptWithinLimit(AttemptNumber attempt) {
        if (!maximumAttempts.allows(attempt)) {
            throw new RuleViolationException("challenge " + name + " allows " + maximumAttempts
                    + " attempts and attempt " + attempt + " is out of range");
        }
    }

    public void requireRoomForAnotherRound(int scheduledRounds, CategoryId categoryId) {
        bestRounds.filter(rule -> !rule.admitsAnotherRound(scheduledRounds)).ifPresent(rule -> {
            throw new ConflictException("challenge " + name + " counts " + rule.description() + " and category "
                    + categoryId.value() + " already has " + rule.outOf() + " rounds of it scheduled");
        });
    }

    private void validateAgainst(MetricDefinition definition, MeasurementSet measurements) {
        measurements.find(definition.key()).ifPresentOrElse(definition::validate, () -> {
            if (definition.isRequired()) {
                throw new RuleViolationException(
                        "challenge " + name + " requires a measurement for " + definition.key().value());
            }
        });
    }

    private Optional<MetricDefinition> definitionOf(MetricKey key) {
        return metrics.stream().filter(metric -> metric.key().equals(key)).findFirst();
    }

    private static <T, K> void requireUnique(List<T> elements, Function<T, K> keyOf, Function<K, String> nameOf,
            String element, String challenge) {
        Set<K> seen = new HashSet<>();
        elements.stream()
                .map(keyOf)
                .filter(key -> !seen.add(key))
                .findFirst()
                .ifPresent(repeated -> {
                    throw new InvalidValueException(
                            element + " " + nameOf.apply(repeated) + " is declared twice in challenge " + challenge);
                });
    }

    private static void requireDefinedMetrics(List<MetricDefinition> metrics, List<ScoringRule> scoringRules,
            String challenge) {
        Set<MetricKey> defined = metrics.stream().map(MetricDefinition::key).collect(Collectors.toSet());
        scoringRules.stream()
                .flatMap(rule -> rule.referencedMetrics().stream())
                .filter(key -> !defined.contains(key))
                .findFirst()
                .ifPresent(undefined -> {
                    throw new InvalidValueException("a scoring rule of challenge " + challenge
                            + " refers to metric " + undefined.value() + ", which the challenge does not define");
                });
    }
}
