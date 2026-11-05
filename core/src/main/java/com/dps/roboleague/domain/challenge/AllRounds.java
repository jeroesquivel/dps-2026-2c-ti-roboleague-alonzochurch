package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.shared.CategoryId;

/** Todas las rondas cuentan y no hay un máximo de rondas por categoría. */
public record AllRounds() implements RoundSelection {

    @Override
    public void requireRoomForAnotherRound(int scheduledRounds, String challenge, CategoryId categoryId) {
        // sin límite de rondas
    }

    @Override
    public String description() {
        return "all the rounds";
    }
}
