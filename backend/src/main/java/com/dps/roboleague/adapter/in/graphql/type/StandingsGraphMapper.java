package com.dps.roboleague.adapter.in.graphql.type;

import com.dps.roboleague.results.ranking.AppliedTiebreak;
import com.dps.roboleague.results.ranking.StandingEntry;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.rules.ranking.RoundOutcome;
import com.dps.roboleague.rules.ranking.ScoreExplanation;
import com.dps.roboleague.rules.ranking.ScoreSubtotal;

/**
 * Traduce una revisión de posiciones del núcleo al tipo GraphQL {@code Standings}. El orden de las filas, los
 * desempates y la selección de rondas ya vienen decididos por el dominio; acá sólo se proyectan.
 */
public final class StandingsGraphMapper {

    private StandingsGraphMapper() {
    }

    public static StandingsGraph toGraph(Standings standings) {
        return new StandingsGraph(standings.revision().number(), standings.status().name(),
                standings.rulebookVersion().toString(), standings.generatedAt().toString(),
                standings.entries().stream().map(StandingsGraphMapper::toGraph).toList());
    }

    private static StandingsGraph.EntryGraph toGraph(StandingEntry entry) {
        return new StandingsGraph.EntryGraph(entry.position(), entry.teamId().value(), entry.totalPoints().value(),
                entry.appliedTiebreaks().stream().map(StandingsGraphMapper::toGraph).toList(),
                toGraph(entry.explanation()));
    }

    private static StandingsGraph.TiebreakGraph toGraph(AppliedTiebreak tiebreak) {
        return new StandingsGraph.TiebreakGraph(tiebreak.code(), tiebreak.description());
    }

    private static StandingsGraph.ExplanationGraph toGraph(ScoreExplanation explanation) {
        return new StandingsGraph.ExplanationGraph(
                explanation.subtotals().stream().map(StandingsGraphMapper::toGraph).toList());
    }

    private static StandingsGraph.SubtotalGraph toGraph(ScoreSubtotal subtotal) {
        return new StandingsGraph.SubtotalGraph(subtotal.code(), subtotal.description(), subtotal.points().value(),
                subtotal.rounds().stream().map(StandingsGraphMapper::toGraph).toList());
    }

    private static StandingsGraph.RoundOutcomeGraph toGraph(RoundOutcome outcome) {
        return new StandingsGraph.RoundOutcomeGraph(outcome.ordinal().value(), outcome.points().value(),
                outcome.status().name(), outcome.reason());
    }
}
