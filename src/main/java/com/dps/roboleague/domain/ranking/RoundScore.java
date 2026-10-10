package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.shared.Points;
import java.util.Objects;

public record RoundScore(RoundOrdinal ordinal, Points points) {

    public RoundScore {
        Objects.requireNonNull(ordinal, "round ordinal is required");
        Objects.requireNonNull(points, "round points are required");
    }
}
