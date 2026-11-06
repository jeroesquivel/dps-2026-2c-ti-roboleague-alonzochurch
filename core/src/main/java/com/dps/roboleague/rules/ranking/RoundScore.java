package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RoundOrdinal;
import java.util.Objects;

public record RoundScore(RoundOrdinal ordinal, Points points) {

    public RoundScore {
        Objects.requireNonNull(ordinal, "round ordinal is required");
        Objects.requireNonNull(points, "round points are required");
    }
}
