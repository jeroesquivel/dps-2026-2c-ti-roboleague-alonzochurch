package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.RulebookVersion;
import java.util.Objects;

/**
 * Cómo cuentan las rondas de un desafío: la política que regía en la versión del reglamento bajo la que se jugaron.
 * Cada ronda jugada la lleva consigo, así un reglamento posterior no reescribe el puntaje del historial.
 */
public record RoundPolicy(RulebookVersion version, RoundSelection selection, AttemptAggregation aggregation) {

    public RoundPolicy {
        Objects.requireNonNull(version, "rulebook version is required");
        Objects.requireNonNull(selection, "round selection is required");
        Objects.requireNonNull(aggregation, "attempt aggregation is required");
    }
}
