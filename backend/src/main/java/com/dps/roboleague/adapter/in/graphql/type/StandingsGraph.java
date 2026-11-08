package com.dps.roboleague.adapter.in.graphql.type;

import java.math.BigDecimal;
import java.util.List;

/** Tipo GraphQL {@code Standings} y los tipos de su explicación. */
public record StandingsGraph(int revision, String status, String rulebookVersion, String generatedAt,
        List<EntryGraph> entries) {

    /** Tipo GraphQL {@code StandingEntry}. {@code teamId} no está en el esquema: lo usa el resolver de {@code team}. */
    public record EntryGraph(int position, String teamId, BigDecimal totalPoints, List<TiebreakGraph> appliedTiebreaks,
            ExplanationGraph explanation) {
    }

    public record TiebreakGraph(String code, String description) {
    }

    public record ExplanationGraph(List<SubtotalGraph> subtotals) {
    }

    public record SubtotalGraph(String code, String description, BigDecimal points, List<RoundOutcomeGraph> rounds) {
    }

    public record RoundOutcomeGraph(int ordinal, BigDecimal points, String status, String reason) {
    }
}
