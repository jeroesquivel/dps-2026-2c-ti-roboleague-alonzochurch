package com.dps.roboleague.adapter.in.graphql.type;

import java.math.BigDecimal;
import java.util.List;

/** Proyección GraphQL del desglose explicable calculado por el núcleo. */
public record ScoreGraph(String completionStatus, List<String> missingSources, String rulebookVersion,
        BigDecimal total, List<ContributionGraph> contributions, BonusCapGraph bonusCap, BySourceGraph bySource) {

    public record ContributionGraph(String ruleCode, String kind, String explanation, BigDecimal points) {
    }

    public record BonusCapGraph(BigDecimal obtained, BigDecimal cap, BigDecimal trimmed, BigDecimal applied) {
    }

    public record BySourceGraph(List<SourceGraph> sources, GroupGraph acrossSources) {
    }

    public record SourceGraph(String source, BigDecimal subtotal, List<ContributionGraph> contributions) {
    }

    public record GroupGraph(BigDecimal subtotal, List<ContributionGraph> contributions) {
    }
}
