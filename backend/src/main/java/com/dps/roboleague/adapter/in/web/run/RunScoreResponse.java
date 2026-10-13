package com.dps.roboleague.adapter.in.web.run;

import java.math.BigDecimal;
import java.util.List;

/**
 * Puntaje explicado de una corrida, calculado por el núcleo con el reglamento fijado en ella.
 *
 * <p>{@code completionStatus} es {@code COMPLETE} o {@code PENDING}. Una corrida pendiente no tiene
 * puntaje: {@code total}, {@code contributions}, {@code bonusCap} y {@code bySource} son {@code null}
 * y {@code missingSources} indica qué fuentes faltan ({@code AUTOMATIC} o {@code JUDGES}). En una
 * corrida completa, la suma de {@code contributions} es {@code total}.
 *
 * <p>{@code bonusCap} sólo está presente si el desafío limita la suma de bonificaciones;
 * {@code bySource} sólo en desafíos mixtos, y entonces la suma de los subtotales de sus fuentes más el
 * de {@code acrossSources} también es {@code total}.
 */
public record RunScoreResponse(String runId, String teamId, String challengeId, String rulebookVersion,
        String completionStatus, List<String> missingSources, BigDecimal total,
        List<ContributionResponse> contributions, BonusCapResponse bonusCap, BySourceResponse bySource) {

    /** {@code kind} es {@code EARNED}, {@code BONUS} o {@code PENALTY}; las penalidades suman puntos negativos. */
    public record ContributionResponse(String ruleCode, String kind, String explanation, BigDecimal points) {
    }

    /** Lo obtenido en bonificaciones, el tope, el recorte (positivo) y lo aplicado ({@code obtained - trimmed}). */
    public record BonusCapResponse(BigDecimal obtained, BigDecimal cap, BigDecimal trimmed, BigDecimal applied) {
    }

    public record BySourceResponse(List<SourceResponse> sources, GroupResponse acrossSources) {
    }

    public record SourceResponse(String source, BigDecimal subtotal, List<ContributionResponse> contributions) {
    }

    public record GroupResponse(BigDecimal subtotal, List<ContributionResponse> contributions) {
    }
}
