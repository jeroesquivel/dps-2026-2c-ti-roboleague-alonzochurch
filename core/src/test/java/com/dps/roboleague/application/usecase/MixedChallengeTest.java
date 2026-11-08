package com.dps.roboleague.application.usecase;

import com.dps.roboleague.appeals.port.in.AcceptAppeal;
import com.dps.roboleague.appeals.port.in.SubmitAppeal;
import com.dps.roboleague.infrastructure.config.RoboLeagueCompositionRoot;
import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.port.in.CaptureRunResult;
import com.dps.roboleague.results.port.in.CorrectResult;
import com.dps.roboleague.results.port.in.GenerateStandings;
import com.dps.roboleague.results.port.in.PublishStandings;
import com.dps.roboleague.results.port.in.RecalculateStandings;
import com.dps.roboleague.results.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.results.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.results.ranking.PendingRun;
import com.dps.roboleague.results.ranking.PublicationStatus;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.results.result.CompletionStatus;
import com.dps.roboleague.results.result.DataEntryError;
import com.dps.roboleague.results.result.ResultAmendment;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunStatus;
import com.dps.roboleague.results.result.SourceReceipt;
import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.challenge.ResultSource;
import com.dps.roboleague.rules.challenge.SourceBreakdown;
import com.dps.roboleague.rules.challenge.SourcedScore;
import com.dps.roboleague.rules.scoring.ContributionKind;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.rules.scoring.rule.PenaltyScoringRule;
import com.dps.roboleague.rules.scoring.rule.PrecisionScoringRule;
import com.dps.roboleague.rules.scoring.rule.TimeScoringRule;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.JudgeId;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RuleViolationException;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.port.in.FindAuditTrail;
import com.dps.roboleague.support.AdjustableClock;
import com.dps.roboleague.support.ShowcaseFixture;
import com.dps.roboleague.support.TestEdition;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedChallengeTest {

    private static final Actor TRACK = Actor.of("track-operator");
    private static final Actor HEAD_JUDGE = Actor.of("head-judge");

    private final AdjustableClock clock = new AdjustableClock(TestEdition.NOW);
    private final TestEdition edition = TestEdition.start(RoboLeagueCompositionRoot.inMemory(clock));
    private final TeamId delta = edition.registerEligibleTeam("Delta Bots");
    private final TeamId omega = edition.registerEligibleTeam("Omega Crew");

    @Test
    void theAutomaticSourceAndThenThePanelCompleteASingleRun() {
        RoundId round = mixedRound(1);

        RunId first = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());
        RunId second = panel(round, delta, 1, ShowcaseFixture.exampleEvaluations());

        RunResult run = edition.runResult(first);
        assertEquals(first, second);
        assertEquals(CompletionStatus.COMPLETE, run.completion().status());
        assertEquals(ShowcaseFixture.exampleMeasurements(), run.originalMeasurements());
        assertEquals(ShowcaseFixture.exampleEvaluations(), run.evaluations());
        assertEquals(ShowcaseFixture.exampleIncidents(), run.originalIncidents());
    }

    @Test
    void thePanelCanArriveBeforeTheMeasurements() {
        RoundId round = mixedRound(1);

        RunId first = panel(round, delta, 1, ShowcaseFixture.exampleEvaluations());
        RunId second = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());

        assertEquals(first, second);
        assertEquals(CompletionStatus.COMPLETE, edition.runResult(first).completion().status());
    }

    @Test
    void aSourceIsRegisteredOnlyOnceAndKeepsItsOriginalData() {
        RoundId round = mixedRound(1);
        RunId run = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());

        assertThrows(ConflictException.class,
                () -> automatic(round, delta, 1, ShowcaseFixture.measurements("60", "0.80")));

        assertEquals(MetricValue.of(70), edition.runResult(run).originalMeasurements().require(ShowcaseFixture.TIME));
        assertEquals(List.of(AuditAction.RESULT_SOURCE_RECEIVED), edition.auditActionsFor(run));
    }

    @Test
    void aSourceCarryingDataOfTheOtherSourceOrIncompleteIsRejectedWithoutCreatingTheRun() {
        RoundId round = mixedRound(1);
        JudgeEvaluations outsider = JudgeEvaluations.of(
                ShowcaseFixture.evaluation(JudgeId.of("J3"), ShowcaseFixture.CREATIVITY, 8));

        assertThrows(RuleViolationException.class, () -> automatic(round, delta, 1,
                ShowcaseFixture.exampleMeasurements().with(ShowcaseFixture.CREATIVITY, MetricValue.of(8))));
        assertThrows(RuleViolationException.class, () -> automatic(round, delta, 1,
                MeasurementSet.empty().with(ShowcaseFixture.TIME, MetricValue.of(70))));
        assertThrows(RuleViolationException.class, () -> panel(round, delta, 1, outsider));

        assertThrows(NotFoundException.class, () -> edition.runResult(RunId.of("RUN-1")));
        assertThrows(NotFoundException.class, () -> edition.runResult(RunId.of("RUN-2")));
        assertThrows(NotFoundException.class, () -> edition.runResult(RunId.of("RUN-3")));
    }

    @Test
    void bothSourcesRespectTheAttemptLimitAndTheFinalStandings() {
        RoundId round = mixedRound(1);

        assertThrows(RuleViolationException.class,
                () -> automatic(round, delta, 3, ShowcaseFixture.exampleMeasurements()));
        assertThrows(RuleViolationException.class,
                () -> panel(round, delta, 3, ShowcaseFixture.exampleEvaluations()));

        generate();
        publish();
        assertThrows(ConflictException.class, () -> automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements()));
        assertThrows(ConflictException.class, () -> panel(round, delta, 1, ShowcaseFixture.exampleEvaluations()));
    }

    @Test
    void aChallengeThatIsNotMixedOnlyTakesTheSingleCapture() {
        RoundId round = edition.scheduleRoundFor(1, List.of(delta));

        assertThrows(RuleViolationException.class, () -> automatic(round, delta, 1,
                edition.measurements("55", 5, "35")));
        assertThrows(RuleViolationException.class, () -> panel(round, delta, 1, edition.evaluations(List.of(8))));

        RunId captured = edition.capture(round, delta, "55", 5, "35", List.of(8), List.of());
        assertThrows(RuleViolationException.class, () -> automatic(round, delta, 1,
                edition.measurements("55", 5, "35")));
        assertEquals(CompletionStatus.COMPLETE, edition.runResult(captured).completion().status());
        assertEquals(List.of(AuditAction.RESULT_CAPTURED), edition.auditActionsFor(captured));
    }

    @Test
    void aRunWaitingForThePanelShowsItIsPendingAndHasNoScore() {
        RunId run = automatic(mixedRound(1), delta, 1, ShowcaseFixture.exampleMeasurements());

        RunResult pending = edition.runResult(run);
        RuleViolationException noScore = assertThrows(RuleViolationException.class, () -> score(run));

        assertEquals(CompletionStatus.PENDING, pending.completion().status());
        assertEquals(List.of(ResultSource.JUDGES), pending.completion().missing());
        assertEquals(new SourceReceipt(ResultSource.AUTOMATIC, TRACK, TestEdition.NOW),
                pending.completion().receiptOf(ResultSource.AUTOMATIC).orElseThrow());
        assertTrue(noScore.getMessage().contains("pending"));
        assertTrue(noScore.getMessage().contains("JUDGES"));
    }

    @Test
    void aRunWaitingForTheMeasurementsShowsWhichSourceIsMissing() {
        RunId run = panel(mixedRound(1), delta, 1, ShowcaseFixture.exampleEvaluations());

        assertEquals(CompletionStatus.PENDING, edition.runResult(run).completion().status());
        assertEquals(List.of(ResultSource.AUTOMATIC), edition.runResult(run).completion().missing());
    }

    @Test
    void anIncompletePanelNamesTheMissingEvaluationAndLeavesTheRunPending() {
        RoundId round = mixedRound(1);
        RunId run = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());
        JudgeEvaluations withoutJ2Execution = JudgeEvaluations.of(
                ShowcaseFixture.evaluation(ShowcaseFixture.J1, ShowcaseFixture.CREATIVITY, 8),
                ShowcaseFixture.evaluation(ShowcaseFixture.J1, ShowcaseFixture.EXECUTION, 9),
                ShowcaseFixture.evaluation(ShowcaseFixture.J2, ShowcaseFixture.CREATIVITY, 6));

        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> panel(round, delta, 1, withoutJ2Execution));

        assertTrue(error.getMessage().contains("J2"));
        assertTrue(error.getMessage().contains("EXECUTION"));
        assertEquals(List.of(ResultSource.JUDGES), edition.runResult(run).completion().missing());
    }

    @Test
    void evaluationsOfZeroCompleteThePanel() {
        RoundId round = mixedRound(1);
        RunId run = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());

        registerPanel(round, delta, 1, ShowcaseFixture.evaluations(0, 0, 0, 0), List.of());

        SourcedScore bySource = score(run).bySource().orElseThrow();
        assertEquals(CompletionStatus.COMPLETE, edition.runResult(run).completion().status());
        assertEquals(Points.ZERO, bySource.sources().get(1).subtotal());
    }

    @Test
    void theStandingsCountOnlyCompleteRunsAndListThePendingOnes() {
        RoundId round = mixedRound(1);
        RunId complete = completeExampleRun(round, delta, 1);
        RunId deltaPending = automatic(round, delta, 2, ShowcaseFixture.exampleMeasurements());
        RunId omegaPending = panel(round, omega, 1, ShowcaseFixture.exampleEvaluations());

        Standings standings = generate();

        assertEquals(1, standings.entries().size());
        assertEquals(delta, standings.entries().getFirst().teamId());
        assertEquals(Points.of(57), standings.entries().getFirst().totalPoints());
        assertEquals(score(complete).total(), standings.entries().getFirst().totalPoints());
        assertEquals(List.of(
                new PendingRun(omegaPending, omega, round, AttemptNumber.first(), List.of(ResultSource.AUTOMATIC)),
                new PendingRun(deltaPending, delta, round, AttemptNumber.of(2), List.of(ResultSource.JUDGES))),
                standings.pendingRuns().runs());
    }

    @Test
    void aPendingRunBlocksThePublicationAndCannotBeAppealedUntilItIsComplete() {
        RoundId round = mixedRound(1);
        RunId run = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());
        generate();

        ConflictException blocked = assertThrows(ConflictException.class, this::publish);
        assertThrows(RuleViolationException.class, () -> appeal(run, delta));

        assertTrue(blocked.getMessage().contains("1 waiting for JUDGES"));
        assertEquals(PublicationStatus.PROVISIONAL, edition.latestStandings().status());

        panel(round, delta, 1, ShowcaseFixture.exampleEvaluations());
        Standings recalculated = edition.module().recalculateStandingsUseCase().execute(
                new RecalculateStandings.Command(edition.competitionId(), edition.categoryId(), "panel completed",
                        TestEdition.ACTOR));
        assertTrue(recalculated.pendingRuns().runs().isEmpty());
        assertEquals(Points.of(57), recalculated.entryFor(delta).orElseThrow().totalPoints());
        assertEquals(PublicationStatus.FINAL, assertDoesNotThrow(this::publish).status());
    }

    @Test
    void theScoreCombinesBothSourcesAndExplainsEachOneApart() {
        RoundId round = mixedRound(1);
        RunId run = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());
        clock.advance(Duration.ofMinutes(90));
        panel(round, delta, 1, ShowcaseFixture.exampleEvaluations());

        CalculateRunScore.RunScore score = score(run);
        SourcedScore bySource = score.bySource().orElseThrow();
        SourceBreakdown automatic = bySource.sources().get(0);
        SourceBreakdown panel = bySource.sources().get(1);

        assertEquals(Points.of(57), score.total());
        assertEquals(score.total(), bySource.total());
        assertEquals(ResultSource.AUTOMATIC, automatic.source());
        assertEquals(List.of(contribution(TimeScoringRule.CODE.value(), 10),
                contribution(PrecisionScoringRule.CODE.value(), 24)), codesAndPoints(automatic));
        assertEquals(Points.of(34), automatic.subtotal());
        assertEquals(ResultSource.JUDGES, panel.source());
        assertEquals(List.of(contribution(JudgePanelScoringRule.CODE.value(), 14),
                contribution(JudgePanelScoringRule.CODE.value(), 12),
                contribution(PenaltyScoringRule.CODE.value(), -3)), codesAndPoints(panel));
        assertEquals(Points.of(23), panel.subtotal());
        assertEquals(new SourceReceipt(ResultSource.AUTOMATIC, TRACK, TestEdition.NOW),
                score.completion().receiptOf(automatic.source()).orElseThrow());
        assertEquals(new SourceReceipt(ResultSource.JUDGES, HEAD_JUDGE, TestEdition.NOW.plusSeconds(5400)),
                score.completion().receiptOf(panel.source()).orElseThrow());
        assertEquals(Points.of(60), score.breakdown().totalOf(ContributionKind.EARNED));
        assertEquals(Points.of(-3), score.breakdown().totalOf(ContributionKind.PENALTY));
    }

    @Test
    void theArrivalOrderDoesNotChangeTheScore() {
        RoundId round = mixedRound(1);
        RunId automaticFirst = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());
        panel(round, delta, 1, ShowcaseFixture.exampleEvaluations());
        RunId panelFirst = panel(round, delta, 2, ShowcaseFixture.exampleEvaluations());
        automatic(round, delta, 2, ShowcaseFixture.exampleMeasurements());

        assertEquals(score(automaticFirst).breakdown(), score(panelFirst).breakdown());
        assertEquals(score(automaticFirst).bySource(), score(panelFirst).bySource());
    }

    @Test
    void anAcceptedAppealCorrectsTheMeasurementsAndKeepsThePanel() {
        RoundId round = mixedRound(1);
        RunId run = completeExampleRun(round, delta, 1);
        Standings first = generate();

        AppealId appeal = appeal(run, delta);
        edition.module().acceptAppealUseCase().execute(new AcceptAppeal.Command(appeal, "the timer started late",
                ResultAmendment.of(ShowcaseFixture.measurements("80", "0.80"), ShowcaseFixture.exampleIncidents()), HEAD_JUDGE));

        SourcedScore bySource = score(run).bySource().orElseThrow();
        assertEquals(Points.of(29), bySource.sources().get(0).subtotal());
        assertEquals(Points.of(5), score(run).breakdown().totalFor(TimeScoringRule.CODE));
        assertEquals(Points.of(23), bySource.sources().get(1).subtotal());
        assertEquals(Points.of(52), score(run).total());
        assertEquals(Points.of(52), edition.latestStandings().entryFor(delta).orElseThrow().totalPoints());
        assertEquals(Points.of(57), first.entryFor(delta).orElseThrow().totalPoints());
        assertEquals(ShowcaseFixture.exampleEvaluations(), edition.runResult(run).evaluations());
    }

    @Test
    void anAcceptedAppealCanCorrectWhatTheJudgesEvaluated() {
        RoundId round = mixedRound(1);
        RunId run = completeExampleRun(round, delta, 1);
        Standings first = generate();
        JudgeEvaluations corrected = ShowcaseFixture.evaluations(10, 10, 10, 10);

        edition.module().acceptAppealUseCase().execute(new AcceptAppeal.Command(appeal(run, delta),
                "the head judge mistyped the sheet", ResultAmendment
                        .of(ShowcaseFixture.exampleMeasurements(), ShowcaseFixture.exampleIncidents())
                        .replacingEvaluations(corrected), HEAD_JUDGE));

        RunResult stored = edition.runResult(run);
        assertEquals(ShowcaseFixture.exampleEvaluations(), stored.evaluations());
        assertEquals(corrected, stored.currentEvaluations());
        assertEquals(Points.of(66), score(run).total());
        assertEquals(Points.of(66), edition.latestStandings().entryFor(delta).orElseThrow().totalPoints());
        assertEquals(Points.of(57), first.entryFor(delta).orElseThrow().totalPoints());
    }

    @Test
    void aDataEntryErrorOfThePanelIsCorrectedWithoutAnAppealWhileTheStandingsAreProvisional() {
        RoundId round = mixedRound(1);
        RunId run = completeExampleRun(round, delta, 1);
        generate();

        RunResult corrected = edition.module().correctResultUseCase().execute(new CorrectResult.Command(run,
                "a judge typed 6 instead of 9", ResultAmendment
                        .of(ShowcaseFixture.exampleMeasurements(), ShowcaseFixture.exampleIncidents())
                        .replacingEvaluations(ShowcaseFixture.evaluations(8, 9, 9, 7)), HEAD_JUDGE));

        assertEquals(new DataEntryError(), corrected.corrections().latest().orElseThrow().origin());
        assertEquals(RunStatus.CORRECTED, edition.runResult(run).status());
        assertEquals(ShowcaseFixture.exampleEvaluations(), edition.runResult(run).evaluations());
        assertEquals(Points.of(60), score(run).total());
        assertEquals(Points.of(60), edition.latestStandings().entryFor(delta).orElseThrow().totalPoints());
        assertTrue(edition.auditActionsFor(run).contains(AuditAction.RESULT_CORRECTED));
    }

    @Test
    void aCorrectionKeepsTheEvaluationsWhenItOnlyAmendsMeasurements() {
        RoundId round = mixedRound(1);
        RunId run = completeExampleRun(round, delta, 1);

        edition.module().correctResultUseCase().execute(new CorrectResult.Command(run, "timer typo",
                ResultAmendment.of(ShowcaseFixture.measurements("80", "0.80"), ShowcaseFixture.exampleIncidents()),
                HEAD_JUDGE));

        assertEquals(ShowcaseFixture.exampleEvaluations(), edition.runResult(run).currentEvaluations());
        assertEquals(Points.of(52), score(run).total());
    }

    @Test
    void aCorrectionOnlyAcceptsJudgesOfTheHeatAndCriteriaOfTheChallenge() {
        RoundId round = mixedRound(1);
        RunId run = completeExampleRun(round, delta, 1);
        ResultAmendment base = ResultAmendment.of(ShowcaseFixture.exampleMeasurements(),
                ShowcaseFixture.exampleIncidents());
        JudgeEvaluations outsider = JudgeEvaluations.of(
                ShowcaseFixture.evaluation(JudgeId.of("J9"), ShowcaseFixture.CREATIVITY, 5));
        JudgeEvaluations unknownCriterion = JudgeEvaluations.of(
                ShowcaseFixture.evaluation(ShowcaseFixture.J1, MetricKey.of("BOGUS"), 5));

        assertThrows(RuleViolationException.class, () -> edition.module().correctResultUseCase().execute(
                new CorrectResult.Command(run, "wrong judge", base.replacingEvaluations(outsider), HEAD_JUDGE)));
        assertThrows(RuleViolationException.class, () -> edition.module().correctResultUseCase().execute(
                new CorrectResult.Command(run, "wrong criterion", base.replacingEvaluations(unknownCriterion),
                        HEAD_JUDGE)));

        assertEquals(RunStatus.CAPTURED, edition.runResult(run).status());
    }

    @Test
    void onceTheStandingsAreFinalAResultCanOnlyChangeThroughAnAppeal() {
        RoundId round = mixedRound(1);
        RunId run = completeExampleRun(round, delta, 1);
        generate();
        publish();
        ResultAmendment amendment = ResultAmendment
                .of(ShowcaseFixture.exampleMeasurements(), ShowcaseFixture.exampleIncidents())
                .replacingEvaluations(ShowcaseFixture.evaluations(10, 10, 10, 10));

        ConflictException refused = assertThrows(ConflictException.class, () -> edition.module()
                .correctResultUseCase().execute(new CorrectResult.Command(run, "late typo", amendment, HEAD_JUDGE)));
        assertTrue(refused.getMessage().contains("final"));
        assertEquals(RunStatus.CAPTURED, edition.runResult(run).status());

        edition.module().acceptAppealUseCase().execute(new AcceptAppeal.Command(appeal(run, delta),
                "the head judge mistyped the sheet", amendment, HEAD_JUDGE));
        assertEquals(RunStatus.CORRECTED, edition.runResult(run).status());
    }

    @Test
    void aCorrectionNeedsAnExistingRunAndAReason() {
        RoundId round = mixedRound(1);
        RunId run = completeExampleRun(round, delta, 1);
        ResultAmendment amendment = ResultAmendment.of(ShowcaseFixture.exampleMeasurements(),
                ShowcaseFixture.exampleIncidents());

        assertThrows(NotFoundException.class, () -> edition.module().correctResultUseCase()
                .execute(new CorrectResult.Command(RunId.of("RUN-NOPE"), "typo", amendment, HEAD_JUDGE)));
        assertThrows(InvalidValueException.class, () -> edition.module().correctResultUseCase()
                .execute(new CorrectResult.Command(run, " ", amendment, HEAD_JUDGE)));
    }

    @Test
    void theAppealWindowStartsWhenTheRunIsComplete() {
        RoundId round = mixedRound(1);
        RunId deltaRun = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());
        RunId omegaRun = automatic(round, omega, 1, ShowcaseFixture.exampleMeasurements());
        clock.advance(Duration.ofMinutes(90));
        panel(round, delta, 1, ShowcaseFixture.exampleEvaluations());
        panel(round, omega, 1, ShowcaseFixture.exampleEvaluations());

        clock.advance(Duration.ofMinutes(45));
        assertDoesNotThrow(() -> appeal(deltaRun, delta));
        clock.advance(Duration.ofMinutes(16));
        RuleViolationException late = assertThrows(RuleViolationException.class, () -> appeal(omegaRun, omega));

        assertTrue(late.getMessage().contains("appeal window"));
    }

    @Test
    void eachRoundUsesTheIntakeOfItsRulebookVersion() {
        edition.publishRulebook(ShowcaseFixture.rulebook(ShowcaseFixture.singleCaptureShowcase()));
        RoundId singleRound = edition.scheduleRoundFor(ShowcaseFixture.CHALLENGE_ID, 1, List.of(delta));
        edition.publishRulebook(ShowcaseFixture.rulebook(ShowcaseFixture.mixedShowcase()));
        RoundId mixedRound = edition.scheduleRoundFor(ShowcaseFixture.CHALLENGE_ID, 2, List.of(delta));

        RunId captured = captureShowcase(singleRound);
        assertThrows(RuleViolationException.class,
                () -> automatic(singleRound, delta, 2, ShowcaseFixture.exampleMeasurements()));
        assertThrows(RuleViolationException.class, () -> captureShowcase(mixedRound));
        RunId mixed = completeExampleRun(mixedRound, delta, 1);

        assertTrue(score(captured).bySource().isEmpty());
        assertEquals(Points.of(57), score(captured).total());
        assertEquals(Points.of(57), score(mixed).total());
    }

    @Test
    void eachSourceLeavesAnAuditEventAndTheSecondOneRecordsTheCompletion() {
        RoundId round = mixedRound(1);
        RunId run = automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());
        assertThrows(ConflictException.class,
                () -> automatic(round, delta, 1, ShowcaseFixture.measurements("60", "0.80")));
        panel(round, delta, 1, ShowcaseFixture.exampleEvaluations());

        List<AuditEvent> events = edition.module().findAuditTrailUseCase().execute(new FindAuditTrail.Command(run));

        assertEquals(List.of(AuditAction.RESULT_SOURCE_RECEIVED, AuditAction.RESULT_SOURCE_RECEIVED),
                TestEdition.actionsOf(events));
        assertEquals(TRACK, events.get(0).actor());
        assertEquals(Map.of(AuditDetail.SOURCE, "AUTOMATIC", AuditDetail.STATUS, "PENDING",
                AuditDetail.MEASUREMENTS, ShowcaseFixture.exampleMeasurements().toString(),
                AuditDetail.RULEBOOK, "v2"), events.get(0).details());
        assertEquals(HEAD_JUDGE, events.get(1).actor());
        assertEquals("JUDGES", events.get(1).details().get(AuditDetail.SOURCE));
        assertEquals("COMPLETE", events.get(1).details().get(AuditDetail.STATUS));
        assertEquals(ShowcaseFixture.exampleEvaluations().toString(),
                events.get(1).details().get(AuditDetail.EVALUATIONS));
        assertTrue(events.get(1).details().get(AuditDetail.INCIDENTS).contains("RESTART"));
    }

    private RoundId mixedRound(int ordinal) {
        edition.publishRulebook(ShowcaseFixture.rulebook(ShowcaseFixture.mixedShowcase()));
        return edition.scheduleRoundFor(ShowcaseFixture.CHALLENGE_ID, ordinal, List.of(delta, omega));
    }

    private RunId completeExampleRun(RoundId round, TeamId team, int attempt) {
        RunId run = automatic(round, team, attempt, ShowcaseFixture.exampleMeasurements());
        panel(round, team, attempt, ShowcaseFixture.exampleEvaluations());
        return run;
    }

    private RunId automatic(RoundId round, TeamId team, int attempt, MeasurementSet measurements) {
        return edition.module().registerAutomaticMeasurementsUseCase().execute(
                new RegisterAutomaticMeasurements.Command(round, team, AttemptNumber.of(attempt), measurements,
                        TRACK));
    }

    private RunId panel(RoundId round, TeamId team, int attempt, JudgeEvaluations evaluations) {
        return registerPanel(round, team, attempt, evaluations, ShowcaseFixture.exampleIncidents());
    }

    private RunId registerPanel(RoundId round, TeamId team, int attempt, JudgeEvaluations evaluations,
            List<IncidentReport> incidents) {
        return edition.module().registerPanelEvaluationsUseCase().execute(new RegisterPanelEvaluations.Command(
                round, team, AttemptNumber.of(attempt), evaluations, incidents, HEAD_JUDGE));
    }

    private RunId captureShowcase(RoundId round) {
        return edition.module().captureRunResultUseCase().execute(new CaptureRunResult.Command(round, delta,
                AttemptNumber.first(), ShowcaseFixture.exampleMeasurements(), ShowcaseFixture.exampleEvaluations(),
                ShowcaseFixture.exampleIncidents(), TRACK));
    }

    private CalculateRunScore.RunScore score(RunId run) {
        return edition.module().calculateRunScoreUseCase().execute(new CalculateRunScore.Command(run));
    }

    private AppealId appeal(RunId run, TeamId team) {
        return edition.module().submitAppealUseCase().execute(new SubmitAppeal.Command(run, team,
                "the timer started late", Actor.of("captain")));
    }

    @Test
    void publishingRequiresRecalculationWhenThePanelCompletesAPreviouslyPendingRun() {
        RoundId round = mixedRound(1);
        automatic(round, delta, 1, ShowcaseFixture.exampleMeasurements());
        Standings provisional = generate();
        panel(round, delta, 1, ShowcaseFixture.exampleEvaluations());

        assertTrue(assertThrows(ConflictException.class, this::publish).getMessage().contains("recalculate"));
        assertEquals(PublicationStatus.PROVISIONAL, edition.latestStandings().status());
        assertEquals(provisional.revision(), edition.latestStandings().revision());

        edition.module().recalculateStandingsUseCase().execute(new RecalculateStandings.Command(
                edition.competitionId(), edition.categoryId(), "panel completed", TestEdition.ACTOR));
        Standings published = publish();
        assertEquals(PublicationStatus.FINAL, published.status());
        assertTrue(published.pendingRuns().runs().isEmpty());
        assertEquals(Points.of(57), published.entryFor(delta).orElseThrow().totalPoints());
    }

    @Test
    void publishingRefusesToOmitANewCompleteRun() {
        RoundId round = mixedRound(1);
        generate();
        completeExampleRun(round, delta, 1);

        assertTrue(assertThrows(ConflictException.class, this::publish).getMessage().contains("recalculate"));
        assertEquals(PublicationStatus.PROVISIONAL, edition.latestStandings().status());
    }

    private Standings generate() {
        return edition.module().generateStandingsUseCase().execute(new GenerateStandings.Command(
                edition.competitionId(), edition.categoryId(), TestEdition.ACTOR));
    }

    private Standings publish() {
        return edition.module().publishStandingsUseCase().execute(new PublishStandings.Command(
                edition.competitionId(), edition.categoryId(), TestEdition.ACTOR));
    }

    private static String contribution(String code, long points) {
        return code + "=" + Points.of(points);
    }

    private static List<String> codesAndPoints(SourceBreakdown group) {
        return group.breakdown().contributions().stream()
                .map(contribution -> contribution.ruleCode().value() + "=" + contribution.points())
                .toList();
    }
}
