package com.dps.roboleague.adapter.in.web.run;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Corrección de un resultado por un error de carga. {@code measurements} e {@code incidents} son el conjunto
 * completo que reemplaza al vigente. {@code evaluations} es opcional: si no viene, las evaluaciones de los jueces
 * se conservan; si viene, es el conjunto completo que las reemplaza.
 */
public record CorrectResultRequest(String reason, Map<String, BigDecimal> measurements,
        List<IncidentRequest> incidents, List<EvaluationRequest> evaluations, String actor) {
}
