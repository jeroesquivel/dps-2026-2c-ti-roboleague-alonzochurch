package com.dps.roboleague.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.appeal.AppealStatus;
import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.port.in.RejectAppeal;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.ranking.Revision;
import com.dps.roboleague.domain.ranking.StandingEntry;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.ranking.rule.FastestMetricTiebreak;
import com.dps.roboleague.domain.ranking.rule.FewestPenaltiesTiebreak;
import com.dps.roboleague.domain.result.ResultCorrection;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunStatus;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.Identifier;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.infrastructure.config.RoboLeagueCompositionRoot;
import com.dps.roboleague.infrastructure.id.SequentialIdGenerator;
import com.dps.roboleague.infrastructure.memory.InMemoryRoundRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRulebookRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRunResultRepository;
import com.dps.roboleague.support.AdjustableClock;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TestEdition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppealRecalculationTest {

    private static final Actor HEAD_JUDGE = Actor.of("head-judge");

    private final TestEdition edition = TestEdition.start();
    private final TeamId delta = edition.registerEligibleTeam("Delta Bots");
    private final TeamId omega = edition.registerEligibleTeam("Omega Crew");

    private RoundId roundId;
    private RunId deltaRun;

    @BeforeEach
    void publishTheFirstStandings() {
        roundId = edition.scheduleRoundFor(1, List.of(delta, omega));
        deltaRun = edition.capture(roundId, delta, "95.5", 4, "42", List.of(8, 9), List.of());
        edition.capture(roundId, omega, "105", 5, "55", List.of(7, 7),
                List.of(IncidentReport.once(RescueEditionFixture.RESTART)));
        generate(edition);
        publish();
    }

    @Test
    void anAcceptedAppealCorrectsTheRunWithoutLosingTheOriginalValues() {
        AppealId appealId = submitAppeal();

        Appeal accepted = accept(appealId, edition.measurements("95.5", 5, "42"), List.of());

        RunResult run = edition.runResult(deltaRun);
        ResultCorrection correction = run.corrections().entries().getFirst();
        assertEquals(AppealStatus.ACCEPTED, accepted.status());
        assertEquals(accepted.status(), edition.appeal(appealId).status());
        assertEquals(RunStatus.CORRECTED, run.status());
        assertEquals(MetricValue.of(4), run.originalMeasurements().require(RescueEditionFixture.OBJECTIVES));
        assertEquals(MetricValue.of(5), run.currentMeasurements().require(RescueEditionFixture.OBJECTIVES));
        assertEquals(appealId, correction.sourceAppeal());
        assertEquals(HEAD_JUDGE, correction.actor());
        assertEquals(List.of(AuditAction.RESULT_CAPTURED, AuditAction.RESULT_CORRECTED),
                edition.auditActionsFor(deltaRun));
        assertEquals(List.of(AuditAction.APPEAL_SUBMITTED, AuditAction.APPEAL_RESOLVED),
                edition.auditActionsFor(appealId));
    }

    @Test
    void acceptingAnAppealRecalculatesTheStandingsWithTheSameRulebookVersion() {
        accept(submitAppeal(), edition.measurements("95.5", 5, "42"), List.of());

        Standings recalculated = edition.latestStandings();

        assertEquals(Revision.of(2), recalculated.revision());
        assertFalse(recalculated.isFinal());
        assertEquals(RulebookVersion.first(), recalculated.rulebookVersion());
        assertEquals(List.of(delta, omega), recalculated.entries().stream().map(StandingEntry::teamId).toList());
        assertEquals(Points.of("85.75"), recalculated.entryFor(delta).orElseThrow().totalPoints());
        assertTrue(edition.standingsHistory().getFirst().isFinal());
        assertTrue(edition.auditActionsFor(edition.categoryId()).contains(AuditAction.STANDINGS_RECALCULATED));
    }

    @Test
    void aNewRulebookDoesNotReplaceHistoricalScoringOrStandingsTiebreaksDuringRecalculation() {
        accept(submitAppeal(), edition.measurements("124", 5, "52"), List.of());
        RulebookVersion newVersion = edition.publishRulebook(new RulebookDraft(
                List.of(RescueEditionFixture.challengeScoredBy(
                        List.of(new ObjectiveScoringRule(RescueEditionFixture.OBJECTIVES, PointsRate.of(100), 5)))),
                RescueEditionFixture.eligibilityRequirements(), RescueEditionFixture.attemptAggregation(),
                List.of(new FastestMetricTiebreak(RescueEditionFixture.TIME)), RescueEditionFixture.appealWindow()));

        Standings recalculated = edition.module().recalculateStandingsUseCase()
                .execute(new RecalculateStandings.Command(edition.competitionId(), edition.categoryId(),
                        "corrected measurements reviewed after a new rulebook was published", TestEdition.ACTOR));

        assertEquals(RulebookVersion.of(2), newVersion);
        assertEquals(RulebookVersion.first(), recalculated.rulebookVersion());
        assertEquals(Revision.of(3), recalculated.revision());
        assertFalse(recalculated.isFinal());
        assertEquals(Points.of("71.50"), recalculated.entryFor(delta).orElseThrow().totalPoints());
        assertEquals(Points.of("71.50"), recalculated.entryFor(omega).orElseThrow().totalPoints());
        assertEquals(List.of(delta, omega), recalculated.entries().stream().map(StandingEntry::teamId).toList());
        assertEquals(FewestPenaltiesTiebreak.CODE,
                recalculated.entryFor(omega).orElseThrow().appliedTiebreaks().getFirst().code());
        Standings original = edition.standingsHistory().getFirst();
        assertTrue(original.isFinal());
        assertEquals(Points.of("60.75"), original.entryFor(delta).orElseThrow().totalPoints());
    }

    @Test
    void anAppealFromAnotherTeamIsRejectedWithoutSavingOrAuditingIt() {
        InMemoryRunResultRepository runs = new InMemoryRunResultRepository();
        runs.save(edition.runResult(deltaRun));
        InMemoryRoundRepository rounds = new InMemoryRoundRepository();
        rounds.save(edition.round(roundId));
        InMemoryRulebookRepository rulebooks = new InMemoryRulebookRepository();
        rulebooks.save(Rulebook.of(edition.competitionId(), RulebookVersion.first(), TestEdition.FIRST_DAY,
                RescueEditionFixture.rulebook()));
        List<Appeal> savedAppeals = new ArrayList<>();
        List<AuditEvent> recordedEvents = new ArrayList<>();
        AppealRepository appeals = new AppealRepository() {
            @Override
            public void save(Appeal appeal) {
                savedAppeals.add(appeal);
            }

            @Override
            public Optional<Appeal> findById(AppealId id) {
                return savedAppeals.stream().filter(appeal -> appeal.id().equals(id)).findFirst();
            }

            @Override
            public List<Appeal> findByRun(RunId runId) {
                return savedAppeals.stream().filter(appeal -> appeal.runId().equals(runId)).toList();
            }
        };
        AuditLog auditLog = new AuditLog() {
            @Override
            public void record(AuditEvent event) {
                recordedEvents.add(event);
            }

            @Override
            public List<AuditEvent> findBySubject(Identifier subject) {
                return recordedEvents.stream().filter(event -> event.subject().equals(subject)).toList();
            }
        };
        SubmitAppeal submit = new SubmitAppealUseCase(runs, rounds, rulebooks, appeals, new SequentialIdGenerator(),
                auditLog, TestEdition.fixedClock());

        assertThrows(RuleViolationException.class,
                () -> submit.execute(new SubmitAppeal.Command(deltaRun, omega, "claim on another team's run",
                        Actor.of("omega-captain"))));

        assertTrue(savedAppeals.isEmpty());
        assertTrue(recordedEvents.isEmpty());
        assertTrue(appeals.findByRun(deltaRun).isEmpty());
        assertEquals(RunStatus.CAPTURED, edition.runResult(deltaRun).status());
    }

    @Test
    void aRejectedAppealLeavesTheCapturedResultAndTheStandingsUntouched() {
        AppealId appealId = submitAppeal();
        Standings published = edition.latestStandings();

        Appeal rejected = edition.module().rejectAppealUseCase()
                .execute(new RejectAppeal.Command(appealId, "the recordings do not support the claim", HEAD_JUDGE));

        RunResult run = edition.runResult(deltaRun);
        assertEquals(AppealStatus.REJECTED, rejected.status());
        assertEquals(AppealStatus.REJECTED, edition.appeal(appealId).status());
        assertTrue(edition.appeal(appealId).decision().isPresent());
        assertEquals(RunStatus.CAPTURED, run.status());
        assertTrue(run.corrections().isEmpty());
        assertEquals(published, edition.latestStandings());
        assertEquals(List.of(AuditAction.APPEAL_SUBMITTED, AuditAction.APPEAL_RESOLVED),
                edition.auditActionsFor(appealId));
    }

    @Test
    void anAppealIsResolvedOnlyOnceWhetherItWasAcceptedOrRejected() {
        AppealId appealId = submitAppeal();
        edition.module().rejectAppealUseCase()
                .execute(new RejectAppeal.Command(appealId, "the recordings do not support the claim", HEAD_JUDGE));

        assertThrows(ConflictException.class,
                () -> accept(appealId, edition.measurements("95.5", 5, "42"), List.of()));

        assertEquals(AppealStatus.REJECTED, edition.appeal(appealId).status());
        assertEquals(RunStatus.CAPTURED, edition.runResult(deltaRun).status());
        assertEquals(1, edition.standingsHistory().size());
    }

    @Test
    void anInvalidCorrectionLeavesTheAppealTheRunAndTheStandingsAsTheyWere() {
        AppealId appealId = submitAppeal();
        MeasurementSet invalid = MeasurementSet.empty()
                .with(RescueEditionFixture.TIME, MetricValue.of("95.5"))
                .with(RescueEditionFixture.OBJECTIVES, MetricValue.of("4.5"))
                .with(RescueEditionFixture.ENERGY, MetricValue.of("42"));

        assertThrows(RuleViolationException.class, () -> accept(appealId, invalid, List.of()));

        assertEquals(AppealStatus.SUBMITTED, edition.appeal(appealId).status());
        assertEquals(RunStatus.CAPTURED, edition.runResult(deltaRun).status());
        assertTrue(edition.runResult(deltaRun).corrections().isEmpty());
        assertEquals(1, edition.standingsHistory().size());
        assertEquals(List.of(AuditAction.APPEAL_SUBMITTED), edition.auditActionsFor(appealId));
    }

    @Test
    void anAppealIsNotResolvedWhenItReportsAnIncidentTheRulebookDoesNotDefine() {
        AppealId appealId = submitAppeal();
        List<IncidentReport> unknown = List.of(IncidentReport.once(PenaltyCode.of("SABOTAGE")));

        assertThrows(RuleViolationException.class,
                () -> accept(appealId, edition.measurements("95.5", 5, "42"), unknown));

        assertEquals(AppealStatus.SUBMITTED, edition.appeal(appealId).status());
    }

    @Test
    void aRunCanBeAppealedOnlyOnceEvenAfterTheFirstAppealWasResolved() {
        AppealId first = submitAppeal();

        assertThrows(ConflictException.class, this::submitAppeal);
        edition.module().rejectAppealUseCase()
                .execute(new RejectAppeal.Command(first, "the recordings do not support the claim", HEAD_JUDGE));
        ConflictException error = assertThrows(ConflictException.class, this::submitAppeal);

        assertTrue(error.getMessage().contains("already appealed"));
        assertEquals(List.of(AuditAction.RESULT_CAPTURED), edition.auditActionsFor(deltaRun));
    }

    @Test
    void standingsCannotBePublishedWhileAnAppealOfTheCategoryIsPending() {
        AppealId appealId = submitAppeal();
        edition.module().recalculateStandingsUseCase().execute(new RecalculateStandings.Command(
                edition.competitionId(), edition.categoryId(), "review before the appeal hearing", HEAD_JUDGE));

        ConflictException error = assertThrows(ConflictException.class, this::publish);

        assertTrue(error.getMessage().contains(appealId.value()));
        assertFalse(edition.latestStandings().isFinal());
        edition.module().rejectAppealUseCase()
                .execute(new RejectAppeal.Command(appealId, "the recordings do not support the claim", HEAD_JUDGE));
        assertTrue(publish().isFinal());
    }

    @Test
    void anAppealIsAcceptedOnlyWithinTheWindowOfTheRulebookPinnedInTheRun() {
        AdjustableClock clock = new AdjustableClock(TestEdition.NOW);
        TestEdition late = TestEdition.start(RoboLeagueCompositionRoot.inMemory(clock));
        TeamId team = late.registerEligibleTeam("Late Bots");
        RoundId round = late.scheduleRoundFor(1, List.of(team));
        RunId firstAttempt = late.capture(round, team, 1, "95.5", 4, "42", List.of(8, 9), List.of());
        RunId secondAttempt = late.capture(round, team, 2, "90", 5, "40", List.of(8, 9), List.of());

        clock.advance(RescueEditionFixture.appealWindow().length());
        AppealId onTheDeadline = late.module().submitAppealUseCase().execute(
                new SubmitAppeal.Command(firstAttempt, team, "submitted on the deadline", Actor.of("captain")));
        clock.advance(Duration.ofSeconds(1));
        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> late.module().submitAppealUseCase().execute(
                        new SubmitAppeal.Command(secondAttempt, team, "submitted too late", Actor.of("captain"))));

        assertEquals(AppealStatus.SUBMITTED, late.appeal(onTheDeadline).status());
        assertTrue(error.getMessage().contains("appeal window"));
    }

    @Test
    void acceptingAnAppealBeforeAnyStandingsExistOnlyCorrectsTheRun() {
        TestEdition fresh = TestEdition.start();
        TeamId team = fresh.registerEligibleTeam("Fresh Bots");
        RunId run = fresh.capture(fresh.scheduleRoundFor(1, List.of(team)), team, "95.5", 4, "42", List.of(8, 9),
                List.of());
        AppealId appealId = fresh.module().submitAppealUseCase()
                .execute(new SubmitAppeal.Command(run, team, "objective missed by the scorer", Actor.of("captain")));

        fresh.module().acceptAppealUseCase().execute(new AcceptAppeal.Command(appealId, "video review",
                fresh.measurements("95.5", 5, "42"), List.of(), HEAD_JUDGE));

        assertEquals(RunStatus.CORRECTED, fresh.runResult(run).status());
        assertThrows(NotFoundException.class, () -> fresh.module().getStandingsUseCase()
                .execute(new GetStandings.Command(fresh.competitionId(), fresh.categoryId())));
        assertEquals(Revision.first(), generate(fresh).revision());
    }

    private AppealId submitAppeal() {
        return edition.module().submitAppealUseCase().execute(new SubmitAppeal.Command(deltaRun, delta,
                "the fourth objective was completed before the buzzer", Actor.of("delta-captain")));
    }

    private Appeal accept(AppealId appealId, MeasurementSet measurements, List<IncidentReport> incidents) {
        return edition.module().acceptAppealUseCase().execute(new AcceptAppeal.Command(appealId,
                "decision based on the video review", measurements, incidents, HEAD_JUDGE));
    }

    private static Standings generate(TestEdition target) {
        return target.module().generateStandingsUseCase()
                .execute(new GenerateStandings.Command(target.competitionId(), target.categoryId(),
                        TestEdition.ACTOR));
    }

    private Standings publish() {
        return edition.module().publishStandingsUseCase()
                .execute(new PublishStandings.Command(edition.competitionId(), edition.categoryId(),
                        TestEdition.ACTOR));
    }
}
