package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.JudgeId;
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
