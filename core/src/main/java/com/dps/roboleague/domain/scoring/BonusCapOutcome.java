package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.shared.Points;
import java.util.Objects;

/**
 * Resultado de aplicar el tope global de bonificaciones a una corrida: lo obtenido antes del tope,
 * el tope configurado y lo recortado. Es la misma cuenta que produce la contribución
 * {@link BonusCap#CODE}, expuesta como datos para quien necesite mostrarla sin interpretar su texto.
 */
public record BonusCapOutcome(Points obtained, Points cap, Points trimmed) {

    public BonusCapOutcome {
        Objects.requireNonNull(obtained, "the bonuses obtained are required");
        Objects.requireNonNull(cap, "the bonus cap is required");
        Objects.requireNonNull(trimmed, "the trimmed points are required");
    }

    /** Bonificaciones que efectivamente suman al total. */
    public Points applied() {
        return obtained.plus(trimmed.negated());
    }
}
