package com.dps.roboleague.rules.scoring;

import java.util.List;
import java.util.Optional;

/** Qué hace un desafío con las bonificaciones obtenidas: dejarlas libres o recortarlas a un tope. */
public sealed interface BonusPolicy permits NoBonusCap, BonusCap {

    static BonusPolicy uncapped() {
        return new NoBonusCap();
    }

    /** Contribuciones de recorte que se suman al desglose; vacías si no hay tope. */
    List<ScoreContribution> trimContributions(ScoreBreakdown breakdown);

    /** Bonificaciones obtenidas, tope y recorte; vacío si no hay tope. */
    Optional<BonusCapOutcome> capOutcomeOf(ScoreBreakdown breakdown);
}
