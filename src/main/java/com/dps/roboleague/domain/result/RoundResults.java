package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.List;

public record RoundResults(List<RunResult> runs) {

    public RoundResults {
        runs = List.copyOf(runs);
    }

    public void requireUnusedAttempt(TeamId teamId, AttemptNumber attemptNumber) {
        boolean alreadyCaptured = runs.stream()
                .anyMatch(run -> run.teamId().equals(teamId) && run.attemptNumber().equals(attemptNumber));
        if (alreadyCaptured) {
            throw new ConflictException("attempt " + attemptNumber + " of team " + teamId.value()
                    + " was already captured");
        }
    }
}
