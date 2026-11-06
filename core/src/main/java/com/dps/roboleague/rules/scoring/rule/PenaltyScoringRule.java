package com.dps.roboleague.rules.scoring.rule;

import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.PenaltyCode;
import com.dps.roboleague.rules.scoring.PenaltyDefinition;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.rules.scoring.ScoringRule;
import com.dps.roboleague.rules.scoring.ScoringRuleCode;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.Points;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PenaltyScoringRule(Map<PenaltyCode, PenaltyDefinition> catalog) implements ScoringRule {

    public static final ScoringRuleCode CODE = ScoringRuleCode.of("PENALTIES");

    public PenaltyScoringRule {
        catalog = Map.copyOf(catalog);
    }

    public static PenaltyScoringRule of(Collection<PenaltyDefinition> definitions) {
        return new PenaltyScoringRule(definitions.stream()
                .collect(Collectors.toMap(PenaltyDefinition::code, Function.identity(), (first, repeated) -> {
                    throw new InvalidValueException("penalty " + repeated.code().value() + " is defined twice");
                })));
    }

    @Override
    public List<ScoreContribution> apply(ScoringContext context) {
        if (context.incidents().isEmpty()) {
            return List.of(ScoreContribution.penalty(CODE, "no incidents reported", Points.ZERO));
        }
        return context.incidents().stream().map(this::contributionFor).toList();
    }

    @Override
    public Set<MetricKey> referencedMetrics() {
        return Set.of();
    }

    private ScoreContribution contributionFor(IncidentReport incident) {
        PenaltyDefinition definition = catalog.get(incident.code());
        if (definition == null) {
            return ScoreContribution.penalty(CODE, "penalty " + incident.code().value()
                    + " is not defined in the rulebook: no deduction applied", Points.ZERO);
        }
        Points deduction = definition.deduction().times(BigDecimal.valueOf(incident.occurrences())).negated();
        String explanation = "%s applied %d time(s)".formatted(definition.description(), incident.occurrences());
        return ScoreContribution.penalty(CODE, explanation, deduction);
    }
}
