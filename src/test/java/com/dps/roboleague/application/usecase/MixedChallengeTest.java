package com.dps.roboleague.application.usecase;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.challenge.SourceBreakdown;
import com.dps.roboleague.domain.challenge.SourcedScore;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.FindAuditTrail;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.ranking.PendingRun;
import com.dps.roboleague.domain.ranking.PublicationStatus;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.result.CompletionStatus;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.SourceReceipt;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.domain.scoring.rule.PenaltyScoringRule;
import com.dps.roboleague.domain.scoring.rule.PrecisionScoringRule;
import com.dps.roboleague.domain.scoring.rule.TimeScoringRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.infrastructure.config.RoboLeagueCompositionRoot;
import com.dps.roboleague.support.AdjustableClock;
import com.dps.roboleague.support.ShowcaseFixture;
import com.dps.roboleague.support.TestEdition;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
                ShowcaseFixture.measurements("80", "0.80"), ShowcaseFixture.exampleIncidents(), HEAD_JUDGE));

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
