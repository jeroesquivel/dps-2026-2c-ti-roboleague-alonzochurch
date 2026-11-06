package com.dps.roboleague.results.result;

import com.dps.roboleague.enrollment.eligibility.EligibilityVerdict;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.rules.challenge.AttemptLimit;
import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.challenge.MetricDefinition;
import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricKind;
import com.dps.roboleague.rules.challenge.MetricUnit;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.JudgeEvaluation;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.JudgeScore;
import com.dps.roboleague.rules.scoring.PenaltyCode;
import com.dps.roboleague.rules.scoring.PenaltyDefinition;
import com.dps.roboleague.rules.scoring.PointsDeducted;
import com.dps.roboleague.rules.scoring.PointsRate;
import com.dps.roboleague.rules.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.rules.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.scheduling.Heat;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.ArenaId;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.HeatId;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.JudgeId;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RoundOrdinal;
import com.dps.roboleague.shared.RuleViolationException;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.shared.TimeSlot;
import com.dps.roboleague.support.TeamFixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunResultTest {

    private static final MetricKey OBJECTIVES = MetricKey.of("OBJECTIVES");
    private static final MetricKey DESIGN = MetricKey.of("DESIGN");
    private static final PenaltyCode RESTART = PenaltyCode.of("RESTART");
    private static final TeamId TEAM = TeamId.of("TEAM-1");
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
        RunResult corrected = run.applyCorrection(correction(5, CAPTURED_AT.plusSeconds(3600)), CHALLENGE, scheduledRound());

        assertEquals(MetricValue.of(4), corrected.originalMeasurements().require(OBJECTIVES));
        assertEquals(MetricValue.of(5), corrected.currentMeasurements().require(OBJECTIVES));
        assertEquals(RunStatus.CORRECTED, corrected.status());
        assertEquals(run.originalIncidents(), corrected.originalIncidents());
        assertEquals(RunStatus.CAPTURED, run.status());
    }

    @Test
    void scoresWithTheLastCorrectionAndKeepsTheWholeHistory() {
        RunResult corrected = run.applyCorrection(correction(5, CAPTURED_AT.plusSeconds(3600)), CHALLENGE, scheduledRound())
                .applyCorrection(correction(3, CAPTURED_AT.plusSeconds(7200)), CHALLENGE, scheduledRound());

        assertEquals(2, corrected.corrections().entries().size());
        assertEquals(MetricValue.of(3), corrected.scoringContext().measurements().require(OBJECTIVES));
    }

    @Test
    void rejectsCorrectionsThatPredateTheCapture() {
        ResultCorrection early = correction(5, CAPTURED_AT.minusSeconds(60));

        assertThrows(RuleViolationException.class, () -> run.applyCorrection(early, CHALLENGE, scheduledRound()));
    }

    @Test
    void rejectsCorrectionsThatPredateThePreviousCorrection() {
        RunResult corrected = run.applyCorrection(correction(5, CAPTURED_AT.plusSeconds(7200)), CHALLENGE, scheduledRound());
        ResultCorrection outOfOrder = correction(3, CAPTURED_AT.plusSeconds(3600));

        assertThrows(InvalidValueException.class, () -> corrected.applyCorrection(outOfOrder, CHALLENGE, scheduledRound()));
        assertEquals(MetricValue.of(5), corrected.currentMeasurements().require(OBJECTIVES));
    }

    @Test
    void rejectsCorrectedMeasurementsThatTheChallengeWouldRejectWithoutTouchingTheHistory() {
        ResultCorrection unknownMetric = new ResultCorrection(CAPTURED_AT.plusSeconds(60), Actor.of("head-judge"),
                "video review", measurements(5).with(MetricKey.of("BATTERY"), MetricValue.of(1)), List.of(),
                JudgeEvaluations.none(), new AcceptedAppeal(AppealId.of("APPEAL-1")));
        ResultCorrection unknownIncident = new ResultCorrection(CAPTURED_AT.plusSeconds(60), Actor.of("head-judge"),
                "video review", measurements(5), List.of(IncidentReport.once(PenaltyCode.of("SABOTAGE"))),
                JudgeEvaluations.none(), new AcceptedAppeal(AppealId.of("APPEAL-1")));

        assertThrows(RuleViolationException.class, () -> run.applyCorrection(unknownMetric, CHALLENGE, scheduledRound()));
        assertThrows(RuleViolationException.class, () -> run.applyCorrection(unknownIncident, CHALLENGE, scheduledRound()));
        assertTrue(run.corrections().isEmpty());
    }

    @Test
    void rejectsTheRulesOfAChallengeThatDoesNotGovernTheRun() {
        ResultCorrection correction = correction(5, CAPTURED_AT.plusSeconds(60));

        assertThrows(RuleViolationException.class, () -> run.applyCorrection(correction, challenge("SUMO"), scheduledRound()));
        assertTrue(run.corrections().latest().isEmpty());
    }

    @Test
    void capturingPinsTheRoundHeatAndRulebookAfterValidatingTheData() {
        Round round = scheduledRound();

        RunResult captured = RunResult.capture(RunId.of("RUN-2"), round, TEAM, CHALLENGE, AttemptNumber.of(2),
                CAPTURED_AT, measurements(4), JudgeEvaluations.of(evaluation("J1")), List.of());

        assertEquals(round.id(), captured.roundId());
        assertEquals(round.heatFor(TEAM).id(), captured.heatId());
        assertEquals(round.rulebookVersion(), captured.rulebookVersion());
        assertEquals(AttemptNumber.of(2), captured.attemptNumber());
    }

    @Test
    void capturingRejectsDataThatTheRoundOrTheChallengeDoNotAllow() {
        Round round = scheduledRound();
        JudgeEvaluations none = JudgeEvaluations.none();

        assertThrows(RuleViolationException.class, () -> RunResult.capture(RunId.of("RUN-2"), round,
                TeamId.of("TEAM-2"), CHALLENGE, AttemptNumber.first(), CAPTURED_AT, measurements(4), none, List.of()));
        assertThrows(RuleViolationException.class, () -> RunResult.capture(RunId.of("RUN-2"), round, TEAM,
                challenge("SUMO"), AttemptNumber.first(), CAPTURED_AT, measurements(4), none, List.of()));
        assertThrows(RuleViolationException.class, () -> RunResult.capture(RunId.of("RUN-2"), round, TEAM,
                CHALLENGE, AttemptNumber.of(3), CAPTURED_AT, measurements(4), none, List.of()));
        assertThrows(RuleViolationException.class, () -> RunResult.capture(RunId.of("RUN-2"), round, TEAM,
                CHALLENGE, AttemptNumber.first(), CAPTURED_AT, MeasurementSet.empty(), none, List.of()));
        assertThrows(RuleViolationException.class, () -> RunResult.capture(RunId.of("RUN-2"), round, TEAM,
                CHALLENGE, AttemptNumber.first(), CAPTURED_AT, measurements(4), none,
                List.of(IncidentReport.once(PenaltyCode.of("SABOTAGE")))));
        assertThrows(RuleViolationException.class, () -> RunResult.capture(RunId.of("RUN-2"), round, TEAM,
                CHALLENGE, AttemptNumber.first(), CAPTURED_AT, measurements(4),
                JudgeEvaluations.of(evaluation("J9")), List.of()));
    }

    @Test
    void anAttemptIsCapturedOnlyOncePerTeamInTheRound() {
        RoundResults results = new RoundResults(List.of(run));

        ConflictException error = assertThrows(ConflictException.class,
                () -> results.requireUnusedAttempt(TEAM, AttemptNumber.first()));

        assertTrue(error.getMessage().contains("already captured"));
        assertDoesNotThrow(() -> results.requireUnusedAttempt(TEAM, AttemptNumber.of(2)));
        assertDoesNotThrow(() -> results.requireUnusedAttempt(TeamId.of("TEAM-2"), AttemptNumber.first()));
    }

    private RunResult capturedRun() {
        return new RunResult(RunId.of("RUN-1"), RoundId.of("ROUND-1"), HeatId.of("HEAT-1"), TEAM,
                ChallengeId.of("RESCUE"), RulebookVersion.first(), AttemptNumber.first(), CAPTURED_AT,
                measurements(4), JudgeEvaluations.none(), List.of(IncidentReport.once(RESTART)));
    }

    private static Round scheduledRound() {
        RoundId roundId = RoundId.of("ROUND-1");
        TeamRegistration team = new TeamRegistration(TEAM, CompetitionId.of("COMP-1"), CategoryId.of("CAT-1"),
                "Delta Bots", TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot(),
                TeamFixtures.completeDocuments()).resolveWith(new EligibilityVerdict(List.of()));
        return new Round(roundId, CompetitionId.of("COMP-1"), CategoryId.of("CAT-1"), ChallengeId.of("RESCUE"),
                RoundOrdinal.of(1), RulebookVersion.first())
                .schedule(new Heat(HeatId.of("HEAT-1"), roundId, TEAM, ArenaId.of("A1"),
                        new TimeSlot(LocalDateTime.of(2026, 3, 2, 10, 0), Duration.ofMinutes(15)),
                        Set.of(JudgeId.of("J1"))), team);
    }

    private static ChallengeSpec challenge(String id) {
        return ChallengeSpec.builder(ChallengeId.of(id), "Rescue mission")
                .metrics(List.of(
                        MetricDefinition.required(OBJECTIVES, MetricKind.OBJECTIVE_COUNT, MetricUnit.of("objectives")),
                        MetricDefinition.optional(DESIGN, MetricKind.JUDGE_CRITERION, MetricUnit.of("points"))))
                .scoringRules(List.of(new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5),
                        new JudgePanelScoringRule(DESIGN, PointsRate.of(1))))
                .penalties(List.of(new PenaltyDefinition(RESTART, "manual restart", PointsDeducted.of(3))))
                .maximumAttempts(AttemptLimit.of(2))
                .build();
    }

    private static JudgeEvaluation evaluation(String judge) {
        return new JudgeEvaluation(JudgeId.of(judge), DESIGN, JudgeScore.of(8));
    }

    private ResultCorrection correction(int objectives, Instant appliedAt) {
        return new ResultCorrection(appliedAt, Actor.of("head-judge"), "video review", measurements(objectives),
                List.of(), JudgeEvaluations.none(), new AcceptedAppeal(AppealId.of("APPEAL-1")));
    }

    private static MeasurementSet measurements(int objectives) {
        return MeasurementSet.empty().with(OBJECTIVES, MetricValue.of(objectives));
    }
}
