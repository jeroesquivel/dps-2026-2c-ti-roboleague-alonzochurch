package com.dps.roboleague.rules.challenge;

import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.rules.scoring.ScoringRule;
import com.dps.roboleague.shared.RuleViolationException;
import java.util.List;
import java.util.Optional;

/** La corrida se captura completa en una sola operación. */
public record SingleCapture() implements CaptureMode {

    @Override
    public void requireSingleCapture(String challenge) {
        // es el modo esperado
    }

    @Override
    public void requireMixedSources(String challenge) {
        throw new RuleViolationException("challenge " + challenge
                + " is not mixed: its runs are captured in a single operation");
    }

    @Override
    public void requireRulesOfEverySource(List<MetricDefinition> metrics, List<ScoringRule> rules,
            String challenge) {
        // un desafío de captura única no reparte reglas por fuente
    }

    @Override
    public Optional<SourcedScore> scoreBySource(ChallengeSpec challenge, ScoringContext context) {
        return Optional.empty();
    }

    @Override
    public List<MetricDefinition> measuredAmong(List<MetricDefinition> metrics) {
        return metrics;
    }
}
