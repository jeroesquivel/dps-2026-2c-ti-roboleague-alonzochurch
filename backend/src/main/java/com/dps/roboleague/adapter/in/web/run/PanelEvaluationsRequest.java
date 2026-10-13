package com.dps.roboleague.adapter.in.web.run;

import java.util.List;

/** Fuente de jueces de un desafío mixto: evaluaciones del panel e incidentes del intento. */
public record PanelEvaluationsRequest(String roundId, String teamId, Integer attemptNumber,
        List<EvaluationRequest> evaluations, List<IncidentRequest> incidents, String actor) {
}
