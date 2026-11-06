package com.dps.roboleague.results.result;

import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.challenge.ResultSource;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.JudgeId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record AutomaticSubmission(MeasurementSet measurements, Actor actor, Instant receivedAt)
        implements SourceSubmission {

    public AutomaticSubmission {
        Objects.requireNonNull(measurements, "measurements are required");
    }

    @Override
    public SourceReceipt receipt() {
        return new SourceReceipt(ResultSource.AUTOMATIC, actor, receivedAt);
    }

    @Override
    public void validateAgainst(ChallengeSpec challenge, Set<JudgeId> panel) {
        challenge.validateAutomaticSource(measurements);
    }

    @Override
    public ScoringContext addTo(ScoringContext received) {
        return new ScoringContext(measurements, received.evaluations(), received.incidents());
    }
}
