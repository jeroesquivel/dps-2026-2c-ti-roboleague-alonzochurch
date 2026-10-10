package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.List;
import java.util.Objects;

public record PendingRun(RunId runId, TeamId teamId, RoundId roundId, AttemptNumber attemptNumber,
        List<ResultSource> missingSources) {

    public PendingRun {
        Objects.requireNonNull(runId, "run id is required");
        Objects.requireNonNull(teamId, "team id is required");
        Objects.requireNonNull(roundId, "round id is required");
        Objects.requireNonNull(attemptNumber, "attempt number is required");
        missingSources = List.copyOf(missingSources);
        if (missingSources.isEmpty()) {
            throw new InvalidValueException("pending run " + runId.value() + " must miss at least one source");
        }
    }

    public static PendingRun of(RunResult run) {
        return new PendingRun(run.id(), run.teamId(), run.roundId(), run.attemptNumber(),
                run.completion().missing());
    }
}
