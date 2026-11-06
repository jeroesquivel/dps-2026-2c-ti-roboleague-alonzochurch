package com.dps.roboleague.results.result;

import com.dps.roboleague.rules.challenge.AttemptLimit;
import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.challenge.MetricDefinition;
import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricKind;
import com.dps.roboleague.rules.challenge.MetricUnit;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.challenge.ResultSource;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.PointsRate;
import com.dps.roboleague.rules.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.HeatId;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.ShowcaseFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class RunResultRepositoryContractTest {

    private static final MetricKey OBJECTIVES = MetricKey.of("OBJECTIVES");
    private static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");
    private static final TeamId TEAM = TeamId.of("TEAM-1");
    private static final Round ROUND = ShowcaseFixture.scheduledRound("ROUND-1", "HEAT-1", TEAM);
    private static final ChallengeSpec SHOWCASE = ShowcaseFixture.mixedShowcase();

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
        ChallengeSpec challenge = ChallengeSpec.builder(ChallengeId.of("RESCUE"), "Rescue")
                .metrics(List.of(MetricDefinition.required(OBJECTIVES, MetricKind.OBJECTIVE_COUNT,
                        MetricUnit.of("objectives"))))
                .scoringRules(List.of(new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5)))
                .maximumAttempts(AttemptLimit.of(2))
                .build();

        repository.save(run.applyCorrection(new ResultCorrection(NOW.plusSeconds(60), Actor.of("head-judge"),
                "video review", MeasurementSet.empty().with(OBJECTIVES, MetricValue.of(5)), List.of(),
                JudgeEvaluations.none(), new AcceptedAppeal(AppealId.of("APPEAL-1"))), challenge, ROUND));

        assertEquals(RunStatus.CORRECTED, repository.findById(RunId.of("RUN-1")).orElseThrow().status());
    }

    @Test
    void twoSourcesArrivingTogetherNeverLoseOneSilently() {
        RunResultRepository repository = repository();
        RunResult automaticFirst = RunResult.open(RunId.of("RUN-1"), ROUND, TEAM, SHOWCASE, AttemptNumber.first(),
                automatic());
        RunResult panelFirst = RunResult.open(RunId.of("RUN-2"), ROUND, TEAM, SHOWCASE, AttemptNumber.first(),
                panel("head-judge"));
        repository.save(automaticFirst);

        assertThrows(ConflictException.class, () -> repository.save(panelFirst));

        RunResult retried = new RoundResults(repository.findByRound(ROUND.id())).receive(() -> RunId.of("RUN-3"),
                ROUND, TEAM, AttemptNumber.first(), SHOWCASE, panel("head-judge"));
        repository.save(retried);
        RunResult stored = repository.findById(RunId.of("RUN-1")).orElseThrow();
        assertEquals(CompletionStatus.COMPLETE, stored.completion().status());
        assertEquals(1, repository.findByRound(ROUND.id()).size());
    }

    @Test
    void aStaleCopyCannotOverwriteASourceRegisteredMeanwhile() {
        RunResultRepository repository = repository();
        RunResult pending = RunResult.open(RunId.of("RUN-1"), ROUND, TEAM, SHOWCASE, AttemptNumber.first(),
                automatic());
        repository.save(pending);
        RunResult byFirstJudge = pending.receive(ROUND, SHOWCASE, panel("head-judge"));
        RunResult bySecondJudge = pending.receive(ROUND, SHOWCASE, panel("assistant-judge"));
        repository.save(byFirstJudge);

        assertThrows(ConflictException.class, () -> repository.save(bySecondJudge));

        assertEquals(Actor.of("head-judge"), repository.findById(RunId.of("RUN-1")).orElseThrow().completion()
                .receiptOf(ResultSource.JUDGES).orElseThrow().actor());
    }

    private static AutomaticSubmission automatic() {
        return new AutomaticSubmission(ShowcaseFixture.exampleMeasurements(), Actor.of("track-operator"), NOW);
    }

    private static PanelSubmission panel(String judge) {
        return new PanelSubmission(ShowcaseFixture.exampleEvaluations(), List.of(), Actor.of(judge),
                NOW.plusSeconds(60));
    }

    private RunResult run(String runId, String roundId, String teamId, int attempt) {
        return new RunResult(RunId.of(runId), RoundId.of(roundId), HeatId.of("HEAT-1"), TeamId.of(teamId),
                ChallengeId.of("RESCUE"), RulebookVersion.first(), AttemptNumber.of(attempt), NOW,
                MeasurementSet.empty().with(OBJECTIVES, MetricValue.of(4)), JudgeEvaluations.none(), List.of());
    }
}
