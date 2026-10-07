package com.dps.roboleague.domain.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.scoring.PenaltyDefinition;
import com.dps.roboleague.domain.scoring.PointsAmount;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.HeatId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunResultTest {

    private static final MetricKey OBJECTIVES = MetricKey.of("OBJECTIVES");
    private static final PenaltyCode RESTART = PenaltyCode.of("RESTART");
    private static final Instant CAPTURED_AT = Instant.parse("2026-03-02T10:15:00Z");
    private static final ChallengeSpec CHALLENGE = challenge("RESCUE");

    private final RunResult run = capturedRun();

    @Test
    void startsAsCapturedWithoutCorrections() {
        assertEquals(RunStatus.CAPTURED, run.status());
        assertEquals(MetricValue.of(4), run.currentMeasurements().require(OBJECTIVES));
    }

    @Test
    void keepsTheOriginalValuesWhenACorrectionIsApplied() {
        run.applyCorrection(correction(5, CAPTURED_AT.plusSeconds(3600)), CHALLENGE);

        assertEquals(MetricValue.of(4), run.originalMeasurements().require(OBJECTIVES));
        assertEquals(MetricValue.of(5), run.currentMeasurements().require(OBJECTIVES));
        assertEquals(RunStatus.CORRECTED, run.status());
    }

    @Test
    void scoresWithTheLastCorrectionAndKeepsTheWholeHistory() {
        run.applyCorrection(correction(5, CAPTURED_AT.plusSeconds(3600)), CHALLENGE);
        run.applyCorrection(correction(3, CAPTURED_AT.plusSeconds(7200)), CHALLENGE);

        assertEquals(2, run.corrections().entries().size());
        assertEquals(MetricValue.of(3), run.scoringContext().measurements().require(OBJECTIVES));
    }

    @Test
    void rejectsCorrectionsThatPredateTheCapture() {
        ResultCorrection early = correction(5, CAPTURED_AT.minusSeconds(60));

        assertThrows(RuleViolationException.class, () -> run.applyCorrection(early, CHALLENGE));
    }

    @Test
    void rejectsCorrectionsThatPredateThePreviousCorrection() {
        run.applyCorrection(correction(5, CAPTURED_AT.plusSeconds(7200)), CHALLENGE);
        ResultCorrection outOfOrder = correction(3, CAPTURED_AT.plusSeconds(3600));

        assertThrows(InvalidValueException.class, () -> run.applyCorrection(outOfOrder, CHALLENGE));
        assertEquals(MetricValue.of(5), run.currentMeasurements().require(OBJECTIVES));
    }

    @Test
    void rejectsCorrectedMeasurementsThatTheChallengeWouldRejectWithoutTouchingTheHistory() {
        ResultCorrection unknownMetric = new ResultCorrection(CAPTURED_AT.plusSeconds(60), Actor.of("head-judge"),
                "video review", measurements(5).with(MetricKey.of("BATTERY"), MetricValue.of(1)), List.of(),
                Optional.empty());
        ResultCorrection unknownIncident = new ResultCorrection(CAPTURED_AT.plusSeconds(60), Actor.of("head-judge"),
                "video review", measurements(5), List.of(IncidentReport.once(PenaltyCode.of("SABOTAGE"))),
                Optional.empty());

        assertThrows(RuleViolationException.class, () -> run.applyCorrection(unknownMetric, CHALLENGE));
        assertThrows(RuleViolationException.class, () -> run.applyCorrection(unknownIncident, CHALLENGE));
        assertTrue(run.corrections().isEmpty());
    }

    @Test
    void rejectsTheRulesOfAChallengeThatDoesNotGovernTheRun() {
        ResultCorrection correction = correction(5, CAPTURED_AT.plusSeconds(60));

        assertThrows(RuleViolationException.class, () -> run.applyCorrection(correction, challenge("SUMO")));
        assertTrue(run.corrections().latest().isEmpty());
    }

    private RunResult capturedRun() {
        return new RunResult(RunId.of("RUN-1"), RoundId.of("ROUND-1"), HeatId.of("HEAT-1"), TeamId.of("TEAM-1"),
                ChallengeId.of("RESCUE"), RulebookVersion.first(), AttemptNumber.first(), CAPTURED_AT,
                measurements(4), JudgeEvaluations.none(), List.of(IncidentReport.once(RESTART)));
    }

    private static ChallengeSpec challenge(String id) {
        return new ChallengeSpec(ChallengeId.of(id), "Rescue mission",
                List.of(MetricDefinition.required(OBJECTIVES, MetricKind.OBJECTIVE_COUNT, MetricUnit.of("objectives"))),
                List.of(new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5)),
                List.of(new PenaltyDefinition(RESTART, "manual restart", PointsAmount.of(3))), AttemptLimit.of(2));
    }

    private ResultCorrection correction(int objectives, Instant appliedAt) {
        return new ResultCorrection(appliedAt, Actor.of("head-judge"), "video review", measurements(objectives),
                List.of(), Optional.of(AppealId.of("APPEAL-1")));
    }

    private MeasurementSet measurements(int objectives) {
        return MeasurementSet.empty().with(OBJECTIVES, MetricValue.of(objectives));
    }
}
