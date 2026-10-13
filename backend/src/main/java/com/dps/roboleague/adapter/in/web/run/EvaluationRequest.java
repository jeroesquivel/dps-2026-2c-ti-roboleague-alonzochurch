package com.dps.roboleague.adapter.in.web.run;

import java.math.BigDecimal;

/** Evaluación de un juez para un criterio; se identifica por ids y clave, no por el tipo del dominio. */
public record EvaluationRequest(String judgeId, String criterion, BigDecimal score) {
}
