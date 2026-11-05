package com.dps.roboleague.adapter.in.web.appeal;

import com.dps.roboleague.adapter.in.web.run.EvaluationRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Contrato de entrada para aceptar una apelación. {@code correctedMeasurements} y
 * {@code correctedIncidents} son el conjunto completo que reemplaza al resultado actual de la
 * corrida, no diferencias respecto de él. {@code correctedEvaluations} es opcional: si no viene, las evaluaciones
 * de los jueces se conservan; si viene, es el conjunto completo que las reemplaza.
 */
public record AcceptAppealRequest(String rationale, Map<String, BigDecimal> correctedMeasurements,
        List<IncidentRequest> correctedIncidents, List<EvaluationRequest> correctedEvaluations, String reviewer) {

    public record IncidentRequest(String code, Integer occurrences) {
    }
}
