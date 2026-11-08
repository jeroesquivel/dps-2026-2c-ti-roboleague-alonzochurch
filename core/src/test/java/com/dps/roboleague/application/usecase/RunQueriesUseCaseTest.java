package com.dps.roboleague.application.usecase;

import com.dps.roboleague.infrastructure.memory.InMemoryRunResultRepository;
import com.dps.roboleague.results.application.FindRoundRunsUseCase;
import com.dps.roboleague.results.application.FindTeamRunsUseCase;
import com.dps.roboleague.results.port.in.FindRoundRuns;
import com.dps.roboleague.results.port.in.FindTeamRuns;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.HeatId;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunQueriesUseCaseTest {

    @Test
    void findsRunsByRoundAndByTeamThroughSeparatePorts() {
        InMemoryRunResultRepository repository = new InMemoryRunResultRepository();
        repository.save(run("RUN-1", "ROUND-1", "TEAM-1"));
        repository.save(run("RUN-2", "ROUND-2", "TEAM-1"));
        repository.save(run("RUN-3", "ROUND-1", "TEAM-2"));
        FindRoundRuns byRound = new FindRoundRunsUseCase(repository);
        FindTeamRuns byTeam = new FindTeamRunsUseCase(repository);

        assertEquals(List.of("RUN-1", "RUN-3"), byRound.execute(
                new FindRoundRuns.Command(RoundId.of("ROUND-1"))).stream().map(run -> run.id().value()).toList());
        assertEquals(List.of("RUN-1", "RUN-2"), byTeam.execute(
                new FindTeamRuns.Command(TeamId.of("TEAM-1"))).stream().map(run -> run.id().value()).toList());
    }

    private static RunResult run(String runId, String roundId, String teamId) {
        return new RunResult(RunId.of(runId), RoundId.of(roundId), HeatId.of("HEAT-" + runId), TeamId.of(teamId),
                ChallengeId.of("RESCUE"), RulebookVersion.first(), AttemptNumber.first(),
                Instant.parse("2026-03-02T10:00:00Z"), MeasurementSet.empty(), JudgeEvaluations.none(), List.of());
    }
}
