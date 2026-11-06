package com.dps.roboleague.results.result;

import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.InvalidValueException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Una corrección aplicada: el resultado completo que queda vigente, quién lo decidió y por qué origen. */
public record ResultCorrection(Instant appliedAt, Actor actor, String reason, MeasurementSet measurements,
        List<IncidentReport> incidents, JudgeEvaluations evaluations, CorrectionOrigin origin) {

    public ResultCorrection {
        Objects.requireNonNull(appliedAt, "correction timestamp is required");
        Objects.requireNonNull(actor, "correction actor is required");
        Objects.requireNonNull(measurements, "corrected measurements are required");
        Objects.requireNonNull(evaluations, "corrected evaluations are required");
        Objects.requireNonNull(origin, "correction origin is required");
        if (reason == null || reason.isBlank()) {
            throw new InvalidValueException("a correction requires a reason");
        }
        incidents = List.copyOf(incidents);
    }
}
