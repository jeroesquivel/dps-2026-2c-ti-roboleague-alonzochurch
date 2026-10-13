package com.dps.roboleague.adapter.in.web.standings;

import java.math.BigDecimal;
import java.util.List;

/** Una revisión de las posiciones de una categoría, con la explicación de cada puntaje. */
public record StandingsResponse(String competitionId, String categoryId, int revision, String status,
        String generatedAt, String rulebookVersion, List<EntryResponse> entries, List<PendingRunResponse> pendingRuns) {

    public record EntryResponse(int position, String teamId, String teamName, BigDecimal totalPoints,
            List<TiebreakResponse> appliedTiebreaks, ExplanationResponse explanation) {
    }

    public record TiebreakResponse(String code, String description) {
    }

    public record ExplanationResponse(List<SubtotalResponse> subtotals) {
    }

    public record SubtotalResponse(String code, String description, BigDecimal points, List<RoundResponse> rounds) {
    }

    /** Resultado de una ronda dentro de un subtotal: `COUNTED` suma sus puntos y `DISCARDED` no. */
    public record RoundResponse(int ordinal, BigDecimal points, String status, String reason) {
    }

    public record PendingRunResponse(String runId, String teamId, String teamName, String roundId,
            int attemptNumber, List<String> missingSources) {
    }
}
