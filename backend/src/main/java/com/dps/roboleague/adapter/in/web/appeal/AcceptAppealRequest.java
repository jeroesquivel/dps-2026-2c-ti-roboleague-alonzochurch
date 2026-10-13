package com.dps.roboleague.adapter.in.web.appeal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Contrato de entrada para aceptar una apelación. {@code correctedMeasurements} y
 * {@code correctedIncidents} son el conjunto completo que reemplaza al resultado actual de la
 * corrida, no diferencias respecto de él.
 */
public record AcceptAppealRequest(String rationale, Map<String, BigDecimal> correctedMeasurements,
        List<IncidentRequest> correctedIncidents, String reviewer) {

    public record IncidentRequest(String code, Integer occurrences) {
    }
}
