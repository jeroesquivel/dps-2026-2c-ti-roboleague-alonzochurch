package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.TeamId;
import java.util.Objects;
import java.util.Optional;

public record TeamScoreSummary(TeamId teamId, TeamRuns runs, ScoreExplanation explanation) {

    public TeamScoreSummary {
        Objects.requireNonNull(teamId, "team id is required");
        Objects.requireNonNull(runs, "team runs are required");
        Objects.requireNonNull(explanation, "score explanation is required");
    }

    public Points totalPoints() {
        return explanation.total();
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
