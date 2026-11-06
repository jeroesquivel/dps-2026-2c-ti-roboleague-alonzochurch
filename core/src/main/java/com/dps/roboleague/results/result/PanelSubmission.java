package com.dps.roboleague.results.result;

import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.challenge.ResultSource;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.JudgeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PanelSubmission(JudgeEvaluations evaluations, List<IncidentReport> incidents, Actor actor,
        Instant receivedAt) implements SourceSubmission {

    public PanelSubmission {
        Objects.requireNonNull(evaluations, "evaluations are required");
        incidents = List.copyOf(incidents);
    }

    @Override
    public SourceReceipt receipt() {
        return new SourceReceipt(ResultSource.JUDGES, actor, receivedAt);
    }

    @Override
    public void validateAgainst(ChallengeSpec challenge, Set<JudgeId> panel) {
        challenge.validatePanelSource(evaluations, panel, incidents);
    }

    @Override
    public ScoringContext addTo(ScoringContext received) {
        return new ScoringContext(received.measurements(), evaluations, incidents);
    }
}
