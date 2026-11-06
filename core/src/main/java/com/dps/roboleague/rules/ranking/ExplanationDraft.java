package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.RulebookVersion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Borrador de la explicación del puntaje de un equipo. Cada {@link RoundSelection} decide si las rondas de un
 * desafío se juntan en el subtotal común de intentos de su versión de reglamento o forman su propio subtotal.
 */
public final class ExplanationDraft {

    private final Map<RulebookVersion, AttemptAggregation> aggregations = new LinkedHashMap<>();
    private final Map<RulebookVersion, List<ScoredRun>> pooled = new LinkedHashMap<>();
    private final List<ScoreSubtotal> selections = new ArrayList<>();

    /** Reserva el subtotal común de intentos de la versión de esa política, aunque nadie le aporte intentos. */
    public void track(RoundPolicy policy) {
        aggregations.putIfAbsent(policy.version(), policy.aggregation());
        pooled.computeIfAbsent(policy.version(), version -> new ArrayList<>());
    }

    public void pool(RoundPolicy policy, List<ScoredRun> attempts) {
        track(policy);
        pooled.get(policy.version()).addAll(attempts);
    }

    public void select(ScoreSubtotal subtotal) {
        selections.add(subtotal);
    }

    public ScoreExplanation explanation() {
        List<ScoreSubtotal> subtotals = new ArrayList<>();
        pooled.forEach((version, attempts) -> subtotals.add(new TeamRuns(attempts, aggregations.get(version)).subtotal(version)));
        subtotals.addAll(selections);
        return new ScoreExplanation(subtotals);
    }
}
