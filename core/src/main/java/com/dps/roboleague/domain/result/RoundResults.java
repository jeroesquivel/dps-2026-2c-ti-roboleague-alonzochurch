package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.List;
import java.util.function.Supplier;

public record RoundResults(List<RunResult> runs) {

    public RoundResults {
        runs = List.copyOf(runs);
    }

    public void requireUnusedAttempt(TeamId teamId, AttemptNumber attemptNumber) {
        boolean alreadyCaptured = runs.stream().anyMatch(run -> isAttempt(run, teamId, attemptNumber));
        if (alreadyCaptured) {
            throw new ConflictException("attempt " + attemptNumber + " of team " + teamId.value()
                    + " was already captured");
        }
    }

    public RunResult receive(Supplier<RunId> newRunId, Round round, TeamId teamId, AttemptNumber attemptNumber,
            ChallengeSpec challenge, SourceSubmission submission) {
        return runs.stream()
                .filter(run -> isAttempt(run, teamId, attemptNumber))
                .findFirst()
                .map(run -> run.receive(round, challenge, submission))
                .orElseGet(() -> RunResult.open(newRunId.get(), round, teamId, challenge, attemptNumber,
                        submission));
    }

    private static boolean isAttempt(RunResult run, TeamId teamId, AttemptNumber attemptNumber) {
        return run.teamId().equals(teamId) && run.attemptNumber().equals(attemptNumber);
    }
}
