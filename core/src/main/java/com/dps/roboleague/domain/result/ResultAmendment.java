package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import java.util.List;
import java.util.Objects;

/**
 * Lo que se quiere cambiar de un resultado ya cargado: el conjunto completo de mediciones e incidentes, y qué hacer
 * con las evaluaciones de los jueces. Es el mismo pedido sea cual sea el origen de la corrección.
 */
public record ResultAmendment(MeasurementSet measurements, List<IncidentReport> incidents,
        EvaluationsChange evaluations) {

    public ResultAmendment {
        Objects.requireNonNull(measurements, "corrected measurements are required");
        Objects.requireNonNull(incidents, "corrected incidents are required");
        Objects.requireNonNull(evaluations, "the change to the evaluations is required");
        incidents = List.copyOf(incidents);
    }

    /** Corrige mediciones e incidentes y deja las evaluaciones de los jueces como están. */
    public static ResultAmendment of(MeasurementSet measurements, List<IncidentReport> incidents) {
        return new ResultAmendment(measurements, incidents, EvaluationsChange.keep());
    }

    public ResultAmendment replacingEvaluations(JudgeEvaluations corrected) {
        return new ResultAmendment(measurements, incidents, EvaluationsChange.replaceWith(corrected));
    }

    public JudgeEvaluations evaluationsOver(JudgeEvaluations current) {
        return evaluations.over(current);
    }
}
