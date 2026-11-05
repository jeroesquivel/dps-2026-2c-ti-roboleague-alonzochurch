package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.shared.CategoryId;

/** Qué rondas de un desafío cuentan para el total de un equipo: todas, o las mejores N de M. */
public sealed interface RoundSelection permits AllRounds, BestRounds {

    static RoundSelection all() {
        return new AllRounds();
    }

    /** Rechaza programar otra ronda si el desafío ya tiene las que prevé el reglamento. */
    void requireRoomForAnotherRound(int scheduledRounds, String challenge, CategoryId categoryId);

    String description();
}
