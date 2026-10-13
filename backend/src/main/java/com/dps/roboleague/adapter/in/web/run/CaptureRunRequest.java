package com.dps.roboleague.adapter.in.web.run;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Captura completa de un intento de un desafío no mixto. Las mediciones se indican por clave de
 * métrica; las evaluaciones y los incidentes son listas (vacías si no hay).
 */
public record CaptureRunRequest(String roundId, String teamId, Integer attemptNumber,
        Map<String, BigDecimal> measurements, List<EvaluationRequest> evaluations,
        List<IncidentRequest> incidents, String actor) {
}
