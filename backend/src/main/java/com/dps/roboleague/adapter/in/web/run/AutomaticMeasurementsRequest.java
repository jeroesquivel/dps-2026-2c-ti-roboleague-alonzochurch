package com.dps.roboleague.adapter.in.web.run;

import java.math.BigDecimal;
import java.util.Map;

/** Fuente automática de un desafío mixto: sólo las mediciones del intento. */
public record AutomaticMeasurementsRequest(String roundId, String teamId, Integer attemptNumber,
        Map<String, BigDecimal> measurements, String actor) {
}
