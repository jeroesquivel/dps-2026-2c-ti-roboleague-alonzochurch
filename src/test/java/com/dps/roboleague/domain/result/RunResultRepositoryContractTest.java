package com.dps.roboleague.domain.result;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dps.roboleague.domain.challenge.AttemptLimit;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricDefinition;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricKind;
import com.dps.roboleague.domain.challenge.MetricUnit;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.HeatId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class RunResultRepositoryContractTest {

    private static final MetricKey OBJECTIVES = MetricKey.of("OBJECTIVES");
    private static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");

    protected abstract RunResultRepository repository();

    @Test
    void aSecondRunForTheSameAttemptOfTheTeamInTheRoundIsRejected() {
        RunResultRepository repository = repository();
        repository.save(run("RUN-1", "ROUND-1", "TEAM-1", 1));

        assertThrows(ConflictException.class, () -> repository.save(run("RUN-2", "ROUND-1", "TEAM-1", 1)));

        assertEquals(List.of(RunId.of("RUN-1")),
                repository.findByRound(RoundId.of("ROUND-1")).stream().map(RunResult::id).toList());
    }

    @Test
    void otherAttemptsTeamsAndRoundsAreIndependent() {
        RunResultRepository repository = repository();
        repository.save(run("RUN-1", "ROUND-1", "TEAM-1", 1));

        assertDoesNotThrow(() -> repository.save(run("RUN-2", "ROUND-1", "TEAM-1", 2)));
        assertDoesNotThrow(() -> repository.save(run("RUN-3", "ROUND-1", "TEAM-2", 1)));
        assertDoesNotThrow(() -> repository.save(run("RUN-4", "ROUND-2", "TEAM-1", 1)));
        assertEquals(3, repository.findByRound(RoundId.of("ROUND-1")).size());
    }

    @Test
    void savingTheSameRunAgainStoresItsCorrection() {
        RunResultRepository repository = repository();
        RunResult run = run("RUN-1", "ROUND-1", "TEAM-1", 1);
        repository.save(run);
        ChallengeSpec challenge = new ChallengeSpec(ChallengeId.of("RESCUE"), "Rescue",
                List.of(MetricDefinition.required(OBJECTIVES, MetricKind.OBJECTIVE_COUNT, MetricUnit.of("objectives"))),
                List.of(new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5)), List.of(), AttemptLimit.of(2));

        repository.save(run.applyCorrection(new ResultCorrection(NOW.plusSeconds(60), Actor.of("head-judge"),
                "video review", MeasurementSet.empty().with(OBJECTIVES, MetricValue.of(5)), List.of(),
                AppealId.of("APPEAL-1")), challenge));

        assertEquals(RunStatus.CORRECTED, repository.findById(RunId.of("RUN-1")).orElseThrow().status());
    }

    private RunResult run(String runId, String roundId, String teamId, int attempt) {
        return new RunResult(RunId.of(runId), RoundId.of(roundId), HeatId.of("HEAT-1"), TeamId.of(teamId),
                ChallengeId.of("RESCUE"), RulebookVersion.first(), AttemptNumber.of(attempt), NOW,
                MeasurementSet.empty().with(OBJECTIVES, MetricValue.of(4)), JudgeEvaluations.none(), List.of());
    }
}
