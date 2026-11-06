package com.dps.roboleague.rules.challenge;

import com.dps.roboleague.rules.ranking.RoundSelection;
import com.dps.roboleague.rules.scoring.BonusCapOutcome;
import com.dps.roboleague.rules.scoring.BonusPolicy;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.PenaltyCode;
import com.dps.roboleague.rules.scoring.PenaltyDefinition;
import com.dps.roboleague.rules.scoring.ScoreBreakdown;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.rules.scoring.ScoringRule;
import com.dps.roboleague.rules.scoring.rule.PenaltyScoringRule;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.JudgeId;
import com.dps.roboleague.shared.RuleViolationException;
import java.util.Comparator;
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
        RoundSelection roundSelection, BonusPolicy bonusPolicy, CaptureMode captureMode) {

    public ChallengeSpec {
        Objects.requireNonNull(id, "challenge id is required");
        Objects.requireNonNull(maximumAttempts, "maximum attempts are required");
        Objects.requireNonNull(roundSelection, "round selection is required");
        Objects.requireNonNull(bonusPolicy, "bonus policy is required");
        Objects.requireNonNull(captureMode, "capture mode is required");
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
        captureMode.requireRulesOfEverySource(metrics, scoringRules, name);
    }

    public static Builder builder(ChallengeId id, String name) {
        return new Builder(id, name);
    }

    public void validate(MeasurementSet measurements) {
        List<MetricDefinition> measured = measuredMetrics();
        measurements.keys().stream()
                .filter(key -> measured.stream().noneMatch(definition -> definition.key().equals(key)))
                .findFirst()
                .ifPresent(unmeasured -> {
                    throw new RuleViolationException(
                            "metric " + unmeasured.value() + " is not a measurement of challenge " + name);
                });
        measured.forEach(definition -> validateAgainst(definition, measurements));
    }

    public void requireSingleCapture() {
        captureMode.requireSingleCapture(name);
    }

    public void validateAutomaticSource(MeasurementSet measurements) {
        requireMixedSources();
        validate(measurements);
    }

    public void validatePanelSource(JudgeEvaluations evaluations, Set<JudgeId> panel,
            List<IncidentReport> incidents) {
        requireMixedSources();
        validateEvaluations(evaluations, panel);
        evaluations.requireComplete(panel, panelCriteria());
        validateIncidents(incidents);
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
        ScoreBreakdown byRules = scoreByRules(context);
        return new ScoreBreakdown(Stream.of(
                        byRules.contributions().stream(),
                        bonusPolicy.trimContributions(byRules).stream(),
                        PenaltyScoringRule.of(penalties).apply(context).stream())
                .flatMap(Function.identity())
                .toList());
    }

    /** Bonificaciones obtenidas, tope y recorte de una corrida; vacío si el desafío no tiene tope. */
    public Optional<BonusCapOutcome> bonusCapOutcome(ScoringContext context) {
        return bonusPolicy.capOutcomeOf(scoreByRules(context));
    }

    public Optional<SourcedScore> scoreBySource(ScoringContext context) {
        return captureMode.scoreBySource(this, context);
    }

    /** Contribuciones con que el tope recorta las bonificaciones de una corrida; vacío si no hay tope. */
    ScoreBreakdown bonusTrimOf(ScoringContext context) {
        return new ScoreBreakdown(bonusPolicy.trimContributions(scoreByRules(context)));
    }

    public void requireAttemptWithinLimit(AttemptNumber attempt) {
        if (!maximumAttempts.allows(attempt)) {
            throw new RuleViolationException("challenge " + name + " allows " + maximumAttempts
                    + " attempts and attempt " + attempt + " is out of range");
        }
    }

    public void requireRoomForAnotherRound(int scheduledRounds, CategoryId categoryId) {
        roundSelection.requireRoomForAnotherRound(scheduledRounds, name, categoryId);
    }

    private ScoreBreakdown scoreByRules(ScoringContext context) {
        return new ScoreBreakdown(scoringRules.stream()
                .flatMap(rule -> rule.apply(context).stream())
                .toList());
    }

    private List<MetricDefinition> measuredMetrics() {
        return captureMode.measuredAmong(metrics);
    }

    private void requireMixedSources() {
        captureMode.requireMixedSources(name);
    }

    private List<MetricKey> panelCriteria() {
        return scoringRules.stream()
                .flatMap(rule -> rule.referencedMetrics().stream())
                .distinct()
                .filter(key -> definitionOf(key).filter(definition -> definition.isSuppliedBy(ResultSource.JUDGES))
                        .isPresent())
                .sorted(Comparator.comparing(MetricKey::value))
                .toList();
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

    public static final class Builder {

        private final ChallengeId id;
        private final String name;
        private List<MetricDefinition> metrics = List.of();
        private List<ScoringRule> scoringRules = List.of();
        private List<PenaltyDefinition> penalties = List.of();
        private AttemptLimit maximumAttempts;
        private RoundSelection roundSelection = RoundSelection.all();
        private BonusPolicy bonusPolicy = BonusPolicy.uncapped();
        private CaptureMode captureMode = CaptureMode.single();

        private Builder(ChallengeId id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder metrics(List<MetricDefinition> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder scoringRules(List<ScoringRule> scoringRules) {
            this.scoringRules = scoringRules;
            return this;
        }

        public Builder penalties(List<PenaltyDefinition> penalties) {
            this.penalties = penalties;
            return this;
        }

        public Builder maximumAttempts(AttemptLimit maximumAttempts) {
            this.maximumAttempts = maximumAttempts;
            return this;
        }

        public Builder roundSelection(RoundSelection roundSelection) {
            this.roundSelection = roundSelection;
            return this;
        }

        public Builder bonusPolicy(BonusPolicy bonusPolicy) {
            this.bonusPolicy = bonusPolicy;
            return this;
        }

        public Builder captureMode(CaptureMode captureMode) {
            this.captureMode = captureMode;
            return this;
        }

        public ChallengeSpec build() {
            return new ChallengeSpec(id, name, metrics, scoringRules, penalties, maximumAttempts, roundSelection,
                    bonusPolicy, captureMode);
        }
    }
}
