package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricKey;
import java.util.List;
import java.util.Objects;

public record ScoringContext(MeasurementSet measurements, JudgeEvaluations evaluations,
        List<IncidentReport> incidents) {

    public ScoringContext {
        Objects.requireNonNull(measurements, "measurements are required");
        Objects.requireNonNull(evaluations, "evaluations are required");
        incidents = List.copyOf(incidents);
    }

    public static ScoringContext of(MeasurementSet measurements) {
        return new ScoringContext(measurements, JudgeEvaluations.none(), List.of());
    }

    public List<JudgeEvaluation> evaluationsFor(MetricKey criterion) {
        return evaluations.forCriterion(criterion);
    }
}
