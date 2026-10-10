package com.dps.roboleague.domain.ranking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.result.AutomaticSubmission;
import com.dps.roboleague.domain.result.PanelSubmission;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.HeatId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.ShowcaseFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PendingRunsTest {

    private static final TeamId TEAM = TeamId.of("TEAM-1");
    private static final Instant TEN = Instant.parse("2026-03-02T10:00:00Z");

    private final Round round = ShowcaseFixture.scheduledRound("ROUND-1", "HEAT-1", TEAM);
    private final RunResult waitingForJudges = RunResult.open(RunId.of("RUN-1"), round, TEAM,
            ShowcaseFixture.mixedShowcase(), AttemptNumber.first(),
            new AutomaticSubmission(ShowcaseFixture.exampleMeasurements(), Actor.of("track"), TEN));
    private final RunResult waitingForMeasurements = RunResult.open(RunId.of("RUN-2"), round, TEAM,
            ShowcaseFixture.mixedShowcase(), AttemptNumber.of(2),
            new PanelSubmission(ShowcaseFixture.exampleEvaluations(), List.of(), Actor.of("judge"), TEN));
    private final RunResult captured = new RunResult(RunId.of("RUN-3"), RoundId.of("ROUND-1"), HeatId.of("HEAT-1"),
            TEAM, ChallengeId.of("RESCUE"), RulebookVersion.first(), AttemptNumber.first(), TEN,
            MeasurementSet.empty(), JudgeEvaluations.none(), List.of());

    @Test
    void listsOnlyTheRunsThatWaitForASourceWithWhatTheyMiss() {
        PendingRuns pending = PendingRuns.among(List.of(captured, waitingForJudges, waitingForMeasurements));

        assertEquals(List.of(
                new PendingRun(RunId.of("RUN-1"), TEAM, round.id(), AttemptNumber.first(),
                        List.of(ResultSource.JUDGES)),
                new PendingRun(RunId.of("RUN-2"), TEAM, round.id(), AttemptNumber.of(2),
                        List.of(ResultSource.AUTOMATIC))), pending.runs());
    }

    @Test
    void blocksThePublicationCountingTheRunsThatWaitForEachSource() {
        PendingRuns pending = PendingRuns.among(List.of(waitingForJudges, waitingForMeasurements));
        PendingRuns onlyJudges = PendingRuns.among(List.of(waitingForJudges));

        ConflictException both = assertThrows(ConflictException.class, pending::requireNone);
        ConflictException judges = assertThrows(ConflictException.class, onlyJudges::requireNone);

        assertEquals("2 runs are still pending: 1 waiting for AUTOMATIC, 1 waiting for JUDGES", both.getMessage());
        assertEquals("1 runs are still pending: 1 waiting for JUDGES", judges.getMessage());
        assertDoesNotThrow(() -> PendingRuns.among(List.of(captured)).requireNone());
        assertDoesNotThrow(() -> PendingRuns.none().requireNone());
    }

    @Test
    void aPendingRunMissesAtLeastOneSource() {
        assertThrows(InvalidValueException.class, () -> new PendingRun(RunId.of("RUN-1"), TEAM, round.id(),
                AttemptNumber.first(), List.of()));
    }
}
