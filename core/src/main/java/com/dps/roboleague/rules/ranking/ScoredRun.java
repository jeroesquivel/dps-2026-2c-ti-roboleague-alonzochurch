package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.scoring.ScoreBreakdown;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RunId;
import java.util.Objects;

public record ScoredRun(RunId runId, ChallengeId challengeId, MeasurementSet measurements, ScoreBreakdown breakdown) {

    public ScoredRun {
        Objects.requireNonNull(runId, "run id is required");
        Objects.requireNonNull(challengeId, "challenge id is required");
        Objects.requireNonNull(measurements, "measurements are required");
        Objects.requireNonNull(breakdown, "breakdown is required");
    }

    public Points total() {
        return breakdown.total();
    }
}
