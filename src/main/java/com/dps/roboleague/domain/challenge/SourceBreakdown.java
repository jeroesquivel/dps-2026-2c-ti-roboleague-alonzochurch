package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.shared.Points;
import java.util.Objects;

public record SourceBreakdown(ResultSource source, ScoreBreakdown breakdown) {

    public SourceBreakdown {
        Objects.requireNonNull(source, "result source is required");
        Objects.requireNonNull(breakdown, "breakdown of the source is required");
    }

    public Points subtotal() {
        return breakdown.total();
    }
}
