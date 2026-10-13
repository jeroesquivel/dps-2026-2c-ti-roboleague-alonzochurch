package com.dps.roboleague.adapter.in.web.run;

import java.math.BigDecimal;
import java.util.List;

/**
 * Puntaje explicado de una corrida, calculado por el núcleo con el reglamento fijado en ella.
 * {@code bySource} sólo está presente cuando el desafío agrupa el puntaje por fuente.
 */
public record RunScoreResponse(String runId, String teamId, String challengeId, String rulebookVersion,
        BigDecimal total, List<ContributionResponse> contributions, String completionStatus,
        BySourceResponse bySource) {

    /** {@code kind} es {@code EARNED}, {@code BONUS} o {@code PENALTY}. */
    public record ContributionResponse(String ruleCode, String kind, String explanation, BigDecimal points) {
    }

    public record BySourceResponse(List<SourceResponse> sources, GroupResponse acrossSources) {
    }

    public record SourceResponse(String source, BigDecimal subtotal, List<ContributionResponse> contributions) {
    }

    public record GroupResponse(BigDecimal subtotal, List<ContributionResponse> contributions) {
    }
}
