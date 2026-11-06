package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.CategoryId;

/**
 * Qué rondas de un desafío cuentan para el total de un equipo: todas, o las mejores N de M. Es una política
 * del ranking que el reglamento sólo configura, igual que {@link AttemptAggregation}.
 */
public sealed interface RoundSelection permits AllRounds, BestRounds {

    static RoundSelection all() {
        return new AllRounds();
    }

    /** Rechaza programar otra ronda si el desafío ya tiene las que prevé el reglamento. */
    void requireRoomForAnotherRound(int scheduledRounds, String challenge, CategoryId categoryId);

    /** Aporta a la explicación del puntaje las rondas que el equipo jugó de un desafío. */
    void explain(ChallengeRounds rounds, ExplanationDraft draft);

    String description();
}
