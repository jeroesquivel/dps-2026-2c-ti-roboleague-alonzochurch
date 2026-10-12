package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record JudgeEvaluations(List<JudgeEvaluation> evaluations) {

    public JudgeEvaluations {
        evaluations = List.copyOf(evaluations);
        Set<List<Object>> evaluated = new HashSet<>();
        evaluations.stream()
                .filter(evaluation -> !evaluated.add(List.of(evaluation.judge(), evaluation.criterion())))
                .findFirst()
                .ifPresent(repeated -> {
                    throw new InvalidValueException("judge " + repeated.judge().value() + " evaluated criterion "
                            + repeated.criterion().value() + " more than once");
                });
    }

    public static JudgeEvaluations none() {
        return new JudgeEvaluations(List.of());
    }

    public static JudgeEvaluations of(JudgeEvaluation... evaluations) {
        return new JudgeEvaluations(List.of(evaluations));
    }

    public List<JudgeEvaluation> forCriterion(MetricKey criterion) {
        return evaluations.stream().filter(evaluation -> evaluation.criterion().equals(criterion)).toList();
    }

    public Set<MetricKey> criteria() {
        return evaluations.stream().map(JudgeEvaluation::criterion).collect(Collectors.toUnmodifiableSet());
    }

    public void requireEvaluatorsWithin(Set<JudgeId> panel) {
        evaluations.stream()
                .map(JudgeEvaluation::judge)
                .filter(judge -> !panel.contains(judge))
                .findFirst()
                .ifPresent(outsider -> {
                    throw new RuleViolationException(
                            "judge " + outsider.value() + " is not assigned to the heat and cannot evaluate it");
                });
    }

    public void requireComplete(Set<JudgeId> panel, List<MetricKey> criteria) {
        panel.stream()
                .sorted(Comparator.comparing(JudgeId::value))
                .flatMap(judge -> criteria.stream()
                        .filter(criterion -> forCriterion(criterion).stream()
                                .noneMatch(evaluation -> evaluation.judge().equals(judge)))
                        .map(criterion -> "judge " + judge.value() + " has not evaluated criterion "
                                + criterion.value()))
                .findFirst()
                .ifPresent(missing -> {
                    throw new RuleViolationException("the panel evaluations are incomplete: " + missing);
                });
    }

    @Override
    public String toString() {
        return evaluations.stream()
                .map(evaluation -> evaluation.judge().value() + ":" + evaluation.criterion().value() + "="
                        + evaluation.score().value().toPlainString())
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
