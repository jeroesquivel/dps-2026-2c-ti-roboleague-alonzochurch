package com.dps.roboleague.application.usecase;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dps.roboleague.Main;
import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.competition.Category;
import com.dps.roboleague.domain.competition.Competition;
import com.dps.roboleague.domain.eligibility.EligibilityVerdict;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.RejectAppeal;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.ranking.CategoryScoringService;
import com.dps.roboleague.domain.ranking.RankingService;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.Heat;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.ScheduleConflictDetector;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.HeatId;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.TeamRegistration;
import com.dps.roboleague.infrastructure.config.RoboLeagueCompositionRoot;
import com.dps.roboleague.infrastructure.id.SequentialIdGenerator;
import com.dps.roboleague.infrastructure.memory.InMemoryAppealRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryAuditLog;
import com.dps.roboleague.infrastructure.memory.InMemoryCompetitionRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRoundRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRulebookRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRunResultRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryStandingsRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryTeamRegistrationRepository;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import com.dps.roboleague.support.TestEdition;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationFailurePathsTest {

    private static final CompetitionId COMPETITION_ID = CompetitionId.of("COMP-1");
    private static final CategoryId CATEGORY_ID = CategoryId.of("CAT-1");
    private static final CategoryId OTHER_CATEGORY_ID = CategoryId.of("CAT-2");
    private static final TeamId TEAM_ID = TeamId.of("TEAM-1");
    private static final RoundId ROUND_ID = RoundId.of("ROUND-1");
    private static final RunId RUN_ID = RunId.of("RUN-1");
    private static final AppealId APPEAL_ID = AppealId.of("APPEAL-1");
    private static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Actor ACTOR = Actor.of("actor");

    private InMemoryCompetitionRepository competitions;
    private InMemoryRulebookRepository rulebooks;
    private InMemoryTeamRegistrationRepository registrations;
    private InMemoryRoundRepository rounds;
    private InMemoryRunResultRepository runResults;
    private InMemoryStandingsRepository standings;
    private InMemoryAppealRepository appeals;
    private InMemoryAuditLog auditLog;
    private SequentialIdGenerator ids;
    private CategoryScoringService scoring;

    @BeforeEach
    void setUp() {
        competitions = new InMemoryCompetitionRepository();
        rulebooks = new InMemoryRulebookRepository();
        registrations = new InMemoryTeamRegistrationRepository();
        rounds = new InMemoryRoundRepository();
        runResults = new InMemoryRunResultRepository();
        standings = new InMemoryStandingsRepository();
        appeals = new InMemoryAppealRepository();
        auditLog = new InMemoryAuditLog();
        ids = new SequentialIdGenerator();
        scoring = new CategoryScoringService(rounds, runResults, rulebooks, new RankingService());
    }

    @Test
    void inputPortsReportMissingTopLevelEntities() {
        RoboLeagueCompositionRoot module = RoboLeagueCompositionRoot.inMemory(CLOCK);

        assertThrows(NotFoundException.class,
                () -> module.findCompetitionUseCase().execute(CompetitionId.of("MISSING")));
        assertThrows(NotFoundException.class,
                () -> module.findTeamRegistrationUseCase().execute(TeamId.of("MISSING")));
        assertThrows(NotFoundException.class, () -> module.findRoundUseCase().execute(RoundId.of("MISSING")));
        assertThrows(NotFoundException.class, () -> module.findRunResultUseCase().execute(RunId.of("MISSING")));
        assertThrows(NotFoundException.class, () -> module.findAppealUseCase().execute(AppealId.of("MISSING")));
        assertThrows(NotFoundException.class, () -> module.getStandingsUseCase()
                .execute(new GetStandings.Command(COMPETITION_ID, CATEGORY_ID)));
        assertThrows(NotFoundException.class, () -> module.publishStandingsUseCase()
                .execute(new com.dps.roboleague.domain.port.in.PublishStandings.Command(
                        COMPETITION_ID, CATEGORY_ID, ACTOR)));
        assertThrows(NotFoundException.class, () -> module.recalculateStandingsUseCase()
                .execute(new RecalculateStandings.Command(COMPETITION_ID, CATEGORY_ID, "reason", ACTOR)));
        assertThrows(NotFoundException.class, () -> module.calculateRunScoreUseCase()
                .execute(new CalculateRunScore.Command(RUN_ID)));
        assertThrows(NotFoundException.class, () -> module.captureRunResultUseCase()
                .execute(new CaptureRunResult.Command(ROUND_ID, TEAM_ID, AttemptNumber.first(),
                        MeasurementSet.empty(), JudgeEvaluations.none(), List.of(), ACTOR)));
        assertThrows(NotFoundException.class, () -> module.submitAppealUseCase()
                .execute(new SubmitAppeal.Command(RUN_ID, TEAM_ID, "claim", ACTOR)));
        assertThrows(NotFoundException.class, () -> module.acceptAppealUseCase()
                .execute(acceptCommand()));
        assertThrows(NotFoundException.class, () -> module.rejectAppealUseCase()
                .execute(new RejectAppeal.Command(APPEAL_ID, "rationale", ACTOR)));
        assertThrows(NotFoundException.class, () -> module.registerTeamUseCase()
                .execute(registerCommand(COMPETITION_ID)));
        assertThrows(NotFoundException.class, () -> module.generateStandingsUseCase()
                .execute(new GenerateStandings.Command(COMPETITION_ID, CATEGORY_ID, ACTOR)));
        assertThrows(NotFoundException.class, () -> module.scheduleRoundUseCase()
                .execute(scheduleCommand(TEAM_ID, CATEGORY_ID)));
    }

    @Test
    void useCasesReportMissingRulebooks() {
        Competition competition = activeCompetition();
        competitions.save(competition);

        RegisterTeamUseCase register = new RegisterTeamUseCase(competitions, rulebooks, registrations, ids, auditLog,
                CLOCK);
        GenerateStandingsUseCase generate = new GenerateStandingsUseCase(competitions, rulebooks, standings, scoring,
                auditLog, CLOCK);
        ScheduleRoundUseCase schedule = new ScheduleRoundUseCase(competitions, rulebooks, registrations, rounds,
                new ScheduleConflictDetector(), ids, auditLog, CLOCK);

        assertThrows(NotFoundException.class, () -> register.execute(registerCommand(COMPETITION_ID)));
        assertThrows(NotFoundException.class,
                () -> generate.execute(new GenerateStandings.Command(COMPETITION_ID, CATEGORY_ID, ACTOR)));
        assertThrows(NotFoundException.class, () -> schedule.execute(scheduleCommand(TEAM_ID, CATEGORY_ID)));

        Standings current = Standings.provisional(COMPETITION_ID, CATEGORY_ID, RulebookVersion.first(), NOW,
                List.of());
        standings.save(current);
        RecalculateStandingsUseCase recalculate = new RecalculateStandingsUseCase(standings, rulebooks, scoring,
                auditLog, CLOCK);
        assertThrows(NotFoundException.class,
                () -> recalculate.execute(new RecalculateStandings.Command(COMPETITION_ID, CATEGORY_ID, "reason",
                        ACTOR)));

        Round round = scheduledRound();
        rounds.save(round);
        CaptureRunResultUseCase capture = new CaptureRunResultUseCase(rounds, rulebooks, runResults, standings, ids,
                auditLog, CLOCK);
        assertThrows(NotFoundException.class,
                () -> capture.execute(new CaptureRunResult.Command(ROUND_ID, TEAM_ID, AttemptNumber.first(),
                        MeasurementSet.empty(), JudgeEvaluations.none(), List.of(), ACTOR)));
        assertThrows(NotFoundException.class, () -> scoring.scoreRun(run(), COMPETITION_ID));

        runResults.save(run());
        SubmitAppealUseCase submit = new SubmitAppealUseCase(runResults, rounds, rulebooks, appeals, ids, auditLog,
                CLOCK);
        assertThrows(NotFoundException.class,
                () -> submit.execute(new SubmitAppeal.Command(RUN_ID, TEAM_ID, "claim", ACTOR)));
    }

    @Test
    void appealSubmissionReportsAMissingRound() {
        runResults.save(run());
        SubmitAppealUseCase submit = new SubmitAppealUseCase(runResults, rounds, rulebooks, appeals, ids, auditLog,
                CLOCK);

        assertThrows(NotFoundException.class,
                () -> submit.execute(new SubmitAppeal.Command(RUN_ID, TEAM_ID, "claim", ACTOR)));
    }

    @Test
    void schedulingReportsMissingAndMismatchedRegistrations() {
        competitions.save(activeCompetition());
        rulebooks.save(rulebook());
        ScheduleRoundUseCase schedule = new ScheduleRoundUseCase(competitions, rulebooks, registrations, rounds,
                new ScheduleConflictDetector(), ids, auditLog, CLOCK);

        assertThrows(NotFoundException.class, () -> schedule.execute(scheduleCommand(TEAM_ID, CATEGORY_ID)));

        registrations.save(acceptedTeam(OTHER_CATEGORY_ID));
        assertThrows(RuleViolationException.class,
                () -> schedule.execute(scheduleCommand(TEAM_ID, CATEGORY_ID)));
    }

    @Test
    void scoreCalculationReportsAMissingRound() {
        runResults.save(run());
        CalculateRunScoreUseCase calculate = new CalculateRunScoreUseCase(runResults, rounds, scoring);

        assertThrows(NotFoundException.class,
                () -> calculate.execute(new CalculateRunScore.Command(RUN_ID)));
    }

    @Test
    void appealResolutionReportsEachMissingCorrectionDependency() {
        Appeal appeal = new Appeal(APPEAL_ID, RUN_ID, TEAM_ID, "claim", NOW);
        appeals.save(appeal);
        AcceptAppealUseCase accept = new AcceptAppealUseCase(appeals, runResults, rounds, rulebooks, standings,
                new RecalculateStandingsUseCase(standings, rulebooks, scoring, auditLog, CLOCK), auditLog, CLOCK);

        assertThrows(NotFoundException.class, () -> accept.execute(acceptCommand()));
        runResults.save(run());
        assertThrows(NotFoundException.class, () -> accept.execute(acceptCommand()));
        rounds.save(scheduledRound());
        assertThrows(NotFoundException.class, () -> accept.execute(acceptCommand()));
    }

    @Test
    void mainEntryPointRunsTheDemo() {
        assertDoesNotThrow(Main::new);
        assertDoesNotThrow(() -> Main.main(new String[0]));
    }

    private Competition activeCompetition() {
        Competition competition = new Competition(COMPETITION_ID, SeasonId.of("SEASON-1"), "Competition",
                DateRange.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5)),
                List.of(category(CATEGORY_ID), category(OTHER_CATEGORY_ID)), RulebookVersion.first());
        return competition;
    }

    private Category category(CategoryId id) {
        return new Category(id, id.value(), AgeRange.between(12, 17), TeamFixtures.RESCUE_BOT);
    }

    private Rulebook rulebook() {
        return Rulebook.of(COMPETITION_ID, RulebookVersion.first(), LocalDate.of(2026, 3, 1),
                RescueEditionFixture.rulebook());
    }

    private Round scheduledRound() {
        Round round = new Round(ROUND_ID, COMPETITION_ID, CATEGORY_ID, RescueEditionFixture.CHALLENGE_ID,
                RoundOrdinal.of(1), RulebookVersion.first());
        return round.schedule(new Heat(HeatId.of("HEAT-1"), ROUND_ID, TEAM_ID, ArenaId.of("A1"),
                new TimeSlot(LocalDateTime.of(2026, 3, 2, 10, 0), Duration.ofMinutes(15)),
                Set.of(JudgeId.of("J1"))), acceptedTeam(CATEGORY_ID));
    }

    private TeamRegistration acceptedTeam(CategoryId categoryId) {
        return new TeamRegistration(TEAM_ID, COMPETITION_ID, categoryId, "Team", TeamFixtures.eligibleMembers(),
                TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments())
                .resolveWith(new EligibilityVerdict(List.of()));
    }

    private RunResult run() {
        return new RunResult(RUN_ID, ROUND_ID, HeatId.of("HEAT-1"), TEAM_ID,
                RescueEditionFixture.CHALLENGE_ID, RulebookVersion.first(), AttemptNumber.first(), NOW,
                MeasurementSet.empty(), JudgeEvaluations.none(), List.of());
    }

    private RegisterTeam.Command registerCommand(CompetitionId competitionId) {
        return new RegisterTeam.Command(competitionId, CATEGORY_ID, "Team",
                TestEdition.draftsOf(TeamFixtures.eligibleMembers()), TeamFixtures.eligibleRobot(),
                TeamFixtures.completeDocuments(), ACTOR);
    }

    private ScheduleRound.Command scheduleCommand(TeamId teamId, CategoryId categoryId) {
        ScheduleRound.HeatDraft heat = new ScheduleRound.HeatDraft(teamId, ArenaId.of("A1"),
                new TimeSlot(LocalDateTime.of(2026, 3, 2, 10, 0), Duration.ofMinutes(15)),
                Set.of(JudgeId.of("J1")));
        return new ScheduleRound.Command(COMPETITION_ID, categoryId, RescueEditionFixture.CHALLENGE_ID,
                RoundOrdinal.of(1), List.of(heat), ACTOR);
    }

    private AcceptAppeal.Command acceptCommand() {
        return new AcceptAppeal.Command(APPEAL_ID, "rationale", MeasurementSet.empty(), List.of(), ACTOR);
    }
}
