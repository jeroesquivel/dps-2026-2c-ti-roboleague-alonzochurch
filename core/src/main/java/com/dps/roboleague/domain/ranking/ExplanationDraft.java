package com.dps.roboleague.domain.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Borrador de la explicación del puntaje de un equipo. Cada {@link RoundSelection} decide si las rondas de un
 * desafío se juntan en el subtotal común de intentos o forman su propio subtotal de rondas.
 */
public final class ExplanationDraft {

    private final AttemptAggregation aggregation;
    private final List<ScoredRun> pooled = new ArrayList<>();
    private final List<ScoreSubtotal> selections = new ArrayList<>();

    public ExplanationDraft(AttemptAggregation aggregation) {
        this.aggregation = Objects.requireNonNull(aggregation, "attempt aggregation is required");
    }

    public AttemptAggregation aggregation() {
        return aggregation;
    }

    public void pool(List<ScoredRun> attempts) {
        pooled.addAll(attempts);
    }

    public void select(ScoreSubtotal subtotal) {
        selections.add(subtotal);
    }

    public ScoreExplanation explanation() {
        List<ScoreSubtotal> subtotals = new ArrayList<>();
        subtotals.add(new TeamRuns(pooled, aggregation).subtotal());
        subtotals.addAll(selections);
        return new ScoreExplanation(subtotals);
    }
}
