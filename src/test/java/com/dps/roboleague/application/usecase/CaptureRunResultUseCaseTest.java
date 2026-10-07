package com.dps.roboleague.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.application.port.in.CalculateRunScore;
import com.dps.roboleague.application.port.in.CaptureRunResult;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunStatus;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluation;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureRunResultUseCaseTest {

    private final TestEdition edition = TestEdition.start();
    private final TeamId delta = edition.registerEligibleTeam("Delta Bots");
    private final RoundId roundId = edition.scheduleRoundFor(1, List.of(delta));

    @Test
    void capturesTheRunPinningTheRulebookVersionInForce() {
        RunId runId = edition.capture(roundId, delta, "95.5", 4, "42", List.of(8, 9), List.of());

        RunResult stored = edition.runResult(runId);

        assertEquals(RulebookVersion.first(), stored.rulebookVersion());
        assertEquals(RunStatus.CAPTURED, stored.status());
        assertEquals(MetricValue.of(4), stored.originalMeasurements().require(RescueEditionFixture.OBJECTIVES));
        assertEquals(2, stored.evaluations().evaluations().size());
    }

    @Test
    void capturesTheLastAllowedAttemptWithoutReplacingTheFirst() {
        RunId first = edition.capture(roundId, delta, "95.5", 4, "42", List.of(8, 9), List.of());

        RunId second = capture(2, edition.measurements("90", 5, "40"));

        assertNotEquals(first, second);
        assertEquals(AttemptNumber.of(1), edition.runResult(first).attemptNumber());
        assertEquals(AttemptNumber.of(2), edition.runResult(second).attemptNumber());
        assertEquals(MetricValue.of(4), edition.runResult(first).originalMeasurements()
                .require(RescueEditionFixture.OBJECTIVES));
        assertEquals(MetricValue.of(5), edition.runResult(second).originalMeasurements()
                .require(RescueEditionFixture.OBJECTIVES));
        assertEquals(Points.of("60.75"), edition.module().calculateRunScoreUseCase()
                .execute(new CalculateRunScore.Command(first)).total());
        assertEquals(Points.of("80.00"), edition.module().calculateRunScoreUseCase()
                .execute(new CalculateRunScore.Command(second)).total());
    }

    @Test
    void rejectsAnUnknownIncidentAndAllowsTheCorrectedCaptureOfThatAttempt() {
        assertThrows(RuleViolationException.class, () -> edition.capture(roundId, delta, "95.5", 4, "42",
                List.of(8, 9), List.of(IncidentReport.once(PenaltyCode.of("UNKNOWN")))));

        RunId run = edition.capture(roundId, delta, "95.5", 4, "42", List.of(8, 9),
                List.of(IncidentReport.once(RescueEditionFixture.RESTART)));

        CalculateRunScore.RunScore score = edition.module().calculateRunScoreUseCase()
                .execute(new CalculateRunScore.Command(run));
        assertEquals(Points.of("57.75"), score.total());
        assertEquals(Points.of("-3.00"), score.breakdown().totalOf(ContributionKind.PENALTY));
        assertEquals(1, edition.runResult(run).originalIncidents().size());
    }

    @Test
    void rejectsMeasurementsThatTheChallengeDoesNotAccept() {
        MeasurementSet withoutObjectives = MeasurementSet.empty()
                .with(RescueEditionFixture.TIME, MetricValue.of("95.5"))
                .with(RescueEditionFixture.ENERGY, MetricValue.of("42"));

        assertThrows(RuleViolationException.class, () -> capture(1, withoutObjectives));
    }

    @Test
    void rejectsAttemptsBeyondTheLimitOfTheChallenge() {
        assertThrows(RuleViolationException.class, () -> capture(3, edition.measurements("95.5", 4, "42")));
    }

    @Test
    void rejectsCapturingTheSameAttemptTwice() {
        edition.capture(roundId, delta, "95.5", 4, "42", List.of(8, 9), List.of());

        ConflictException error = assertThrows(ConflictException.class,
                () -> capture(1, edition.measurements("90", 5, "40")));

        assertTrue(error.getMessage().contains("already captured"));
    }

    @Test
    void rejectsTeamsWithoutAHeatInTheRound() {
        TeamId omega = edition.registerEligibleTeam("Omega Crew");

        assertThrows(RuleViolationException.class, () -> edition.module().captureRunResultUseCase()
                .execute(new CaptureRunResult.Command(roundId, omega, AttemptNumber.first(),
                        edition.measurements("95.5", 4, "42"), JudgeEvaluations.none(), List.of(),
                        TestEdition.ACTOR)));
    }

    @Test
    void rejectsEvaluationsFromJudgesThatAreNotAssignedToTheHeat() {
        JudgeEvaluations outsider = JudgeEvaluations.of(new JudgeEvaluation(JudgeId.of("J9"),
                RescueEditionFixture.DESIGN, JudgeScore.of(10)));

        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> capture(1, edition.measurements("95.5", 4, "42"), outsider));

        assertTrue(error.getMessage().contains("J9"));
    }

    @Test
    void rejectsEvaluationsOfACriterionThatTheChallengeDoesNotDefine() {
        JudgeEvaluations unknownCriterion = JudgeEvaluations.of(new JudgeEvaluation(JudgeId.of("J1"),
                MetricKey.of("STYLE"), JudgeScore.of(10)));

        assertThrows(RuleViolationException.class,
                () -> capture(1, edition.measurements("95.5", 4, "42"), unknownCriterion));
    }

    @Test
    void aJudgeCannotWeighDoubleByEvaluatingTheSameCriterionTwice() {
        assertThrows(InvalidValueException.class, () -> JudgeEvaluations.of(
                new JudgeEvaluation(JudgeId.of("J1"), RescueEditionFixture.DESIGN, JudgeScore.of(10)),
                new JudgeEvaluation(JudgeId.of("J1"), RescueEditionFixture.DESIGN, JudgeScore.of(10)),
                new JudgeEvaluation(JudgeId.of("J2"), RescueEditionFixture.DESIGN, JudgeScore.of(0))));
    }

    private RunId capture(int attempt, MeasurementSet measurements) {
        return capture(attempt, measurements, JudgeEvaluations.none());
    }

    private RunId capture(int attempt, MeasurementSet measurements, JudgeEvaluations evaluations) {
        return edition.module().captureRunResultUseCase().execute(new CaptureRunResult.Command(roundId, delta,
                AttemptNumber.of(attempt), measurements, evaluations, List.of(), TestEdition.ACTOR));
    }
}
