package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.Objects;
import java.util.Optional;

public record TeamScoreSummary(TeamId teamId, TeamRuns runs) {

    public TeamScoreSummary {
        Objects.requireNonNull(teamId, "team id is required");
        Objects.requireNonNull(runs, "team runs are required");
    }

    public Points totalPoints() {
        return runs.aggregatedPoints();
    }

    public Optional<Points> bestRunPoints() {
        return runs.bestRunPoints();
    }

    public Points penaltyPoints() {
        return runs.penaltyPoints();
    }

    public Optional<MetricValue> lowestMeasurement(MetricKey key) {
        return runs.lowestMeasurement(key);
    }
}
