package com.dps.roboleague.infrastructure.config;

import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.appeals.application.AcceptAppealUseCase;
import com.dps.roboleague.appeals.application.AppealsPendingOnRuns;
import com.dps.roboleague.appeals.application.FindAppealUseCase;
import com.dps.roboleague.appeals.application.RejectAppealUseCase;
import com.dps.roboleague.appeals.application.SubmitAppealUseCase;
import com.dps.roboleague.appeals.port.in.AcceptAppeal;
import com.dps.roboleague.appeals.port.in.FindAppeal;
import com.dps.roboleague.appeals.port.in.RejectAppeal;
import com.dps.roboleague.appeals.port.in.SubmitAppeal;
import com.dps.roboleague.enrollment.application.CreateSeasonUseCase;
import com.dps.roboleague.enrollment.application.FindCompetitionUseCase;
import com.dps.roboleague.enrollment.application.FindCategoryTeamsUseCase;
import com.dps.roboleague.enrollment.application.FindTeamRegistrationUseCase;
import com.dps.roboleague.enrollment.application.FindTeamRegistrationsUseCase;
import com.dps.roboleague.enrollment.application.RegisterTeamUseCase;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.competition.SeasonRepository;
import com.dps.roboleague.enrollment.port.in.CreateSeason;
import com.dps.roboleague.enrollment.port.in.FindCompetition;
import com.dps.roboleague.enrollment.port.in.FindCategoryTeams;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistration;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistrations;
import com.dps.roboleague.enrollment.port.in.RegisterTeam;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.infrastructure.id.SequentialIdGenerator;
import com.dps.roboleague.infrastructure.memory.InMemoryAppealRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryAuditLog;
import com.dps.roboleague.infrastructure.memory.InMemoryCompetitionRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRoundRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRulebookRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRunResultRepository;
import com.dps.roboleague.infrastructure.memory.InMemorySeasonRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryStandingsRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryTeamRegistrationRepository;
import com.dps.roboleague.results.application.CalculateRunScoreUseCase;
import com.dps.roboleague.results.application.CaptureRunResultUseCase;
import com.dps.roboleague.results.application.CorrectResultUseCase;
import com.dps.roboleague.results.application.FindRunResultUseCase;
import com.dps.roboleague.results.application.GenerateStandingsUseCase;
import com.dps.roboleague.results.application.GetStandingsUseCase;
import com.dps.roboleague.results.application.PublishStandingsUseCase;
import com.dps.roboleague.results.application.RecalculateStandingsUseCase;
import com.dps.roboleague.results.application.RegisterAutomaticMeasurementsUseCase;
import com.dps.roboleague.results.application.RegisterPanelEvaluationsUseCase;
import com.dps.roboleague.results.application.ResultCorrector;
import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.port.in.CaptureRunResult;
import com.dps.roboleague.results.port.in.CorrectResult;
import com.dps.roboleague.results.port.in.FindRunResult;
import com.dps.roboleague.results.port.in.GenerateStandings;
import com.dps.roboleague.results.port.in.GetStandings;
import com.dps.roboleague.results.port.in.PublishStandings;
import com.dps.roboleague.results.port.in.RecalculateStandings;
import com.dps.roboleague.results.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.results.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.results.ranking.CategoryScoringService;
import com.dps.roboleague.results.ranking.RankingService;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.rules.application.CreateCompetitionUseCase;
import com.dps.roboleague.rules.application.PublishRulebookUseCase;
import com.dps.roboleague.rules.application.RulebookEligibilityRequirements;
import com.dps.roboleague.rules.port.in.CreateCompetition;
import com.dps.roboleague.rules.port.in.PublishRulebook;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.scheduling.ScheduleConflictDetector;
import com.dps.roboleague.scheduling.application.FindCategoryRoundsUseCase;
import com.dps.roboleague.scheduling.application.FindRoundUseCase;
import com.dps.roboleague.scheduling.application.ScheduleRoundUseCase;
import com.dps.roboleague.scheduling.port.in.FindCategoryRounds;
import com.dps.roboleague.scheduling.port.in.FindRound;
import com.dps.roboleague.scheduling.port.in.ScheduleRound;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.application.FindAuditTrailUseCase;
import com.dps.roboleague.shared.audit.AuditLog;
import com.dps.roboleague.shared.port.in.FindAuditTrail;
import java.time.Clock;

public final class RoboLeagueCompositionRoot {

    private final SeasonRepository seasons;
    private final CompetitionRepository competitions;
    private final RulebookRepository rulebooks;
    private final TeamRegistrationRepository registrations;
    private final RoundRepository rounds;
    private final RunResultRepository runResults;
    private final StandingsRepository standings;
    private final AppealRepository appeals;
    private final AuditLog auditLog;
    private final CategoryScoringService scoringService;
    private final ScheduleConflictDetector conflictDetector = new ScheduleConflictDetector();
    private final IdGenerator idGenerator;
    private final Clock clock;

    public RoboLeagueCompositionRoot(SeasonRepository seasons, CompetitionRepository competitions,
            RulebookRepository rulebooks, TeamRegistrationRepository registrations, RoundRepository rounds,
            RunResultRepository runResults, StandingsRepository standings, AppealRepository appeals, AuditLog auditLog,
            IdGenerator idGenerator, Clock clock) {
        this.seasons = seasons;
        this.competitions = competitions;
        this.rulebooks = rulebooks;
        this.registrations = registrations;
        this.rounds = rounds;
        this.runResults = runResults;
        this.standings = standings;
        this.appeals = appeals;
        this.auditLog = auditLog;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.scoringService = new CategoryScoringService(rounds, runResults, rulebooks, new RankingService());
    }

    public static RoboLeagueCompositionRoot inMemory(Clock clock) {
        return new RoboLeagueCompositionRoot(new InMemorySeasonRepository(), new InMemoryCompetitionRepository(),
                new InMemoryRulebookRepository(), new InMemoryTeamRegistrationRepository(),
                new InMemoryRoundRepository(), new InMemoryRunResultRepository(), new InMemoryStandingsRepository(),
                new InMemoryAppealRepository(), new InMemoryAuditLog(), new SequentialIdGenerator(), clock);
    }


    public CreateSeason createSeasonUseCase() {
        return new CreateSeasonUseCase(seasons, idGenerator, auditLog, clock);
    }

    public CreateCompetition createCompetitionUseCase() {
        return new CreateCompetitionUseCase(seasons, competitions, rulebooks, idGenerator, auditLog, clock);
    }

    public PublishRulebook publishRulebookUseCase() {
        return new PublishRulebookUseCase(competitions, rulebooks, auditLog, clock);
    }

    public RegisterTeam registerTeamUseCase() {
        return new RegisterTeamUseCase(competitions, new RulebookEligibilityRequirements(rulebooks), registrations, idGenerator, auditLog, clock);
    }

    public ScheduleRound scheduleRoundUseCase() {
        return new ScheduleRoundUseCase(competitions, rulebooks, registrations, rounds, conflictDetector, idGenerator,
                auditLog, clock);
    }

    public CaptureRunResult captureRunResultUseCase() {
        return new CaptureRunResultUseCase(rounds, rulebooks, runResults, standings, idGenerator, auditLog, clock);
    }

    public RegisterAutomaticMeasurements registerAutomaticMeasurementsUseCase() {
        return new RegisterAutomaticMeasurementsUseCase(rounds, rulebooks, runResults, standings, idGenerator,
                auditLog, clock);
    }

    public RegisterPanelEvaluations registerPanelEvaluationsUseCase() {
        return new RegisterPanelEvaluationsUseCase(rounds, rulebooks, runResults, standings, idGenerator, auditLog,
                clock);
    }

    public GenerateStandings generateStandingsUseCase() {
        return new GenerateStandingsUseCase(competitions, rulebooks, standings, scoringService, auditLog, clock);
    }

    public PublishStandings publishStandingsUseCase() {
        return new PublishStandingsUseCase(standings, scoringService, new AppealsPendingOnRuns(appeals), auditLog, clock);
    }

    public SubmitAppeal submitAppealUseCase() {
        return new SubmitAppealUseCase(runResults, rounds, rulebooks, appeals, idGenerator, auditLog, clock);
    }

    public AcceptAppeal acceptAppealUseCase() {
        return new AcceptAppealUseCase(appeals, runResults, resultCorrector(), auditLog, clock);
    }

    public RejectAppeal rejectAppealUseCase() {
        return new RejectAppealUseCase(appeals, auditLog, clock);
    }

    public CorrectResult correctResultUseCase() {
        return new CorrectResultUseCase(runResults, standings, resultCorrector());
    }

    private ResultCorrector resultCorrector() {
        return new ResultCorrector(rounds, rulebooks, runResults, standings, recalculateStandingsUseCase(), auditLog,
                clock);
    }

    public RecalculateStandings recalculateStandingsUseCase() {
        return new RecalculateStandingsUseCase(standings, rulebooks, scoringService, auditLog, clock);
    }


    public CalculateRunScore calculateRunScoreUseCase() {
        return new CalculateRunScoreUseCase(runResults, rounds, scoringService);
    }

    public FindCompetition findCompetitionUseCase() {
        return new FindCompetitionUseCase(competitions);
    }

    public FindTeamRegistration findTeamRegistrationUseCase() {
        return new FindTeamRegistrationUseCase(registrations);
    }

    public FindRound findRoundUseCase() {
        return new FindRoundUseCase(rounds);
    }

    public FindTeamRegistrations findTeamRegistrationsUseCase() {
        return new FindTeamRegistrationsUseCase(registrations);
    }

    public FindCategoryTeams findCategoryTeamsUseCase() {
        return new FindCategoryTeamsUseCase(competitions, registrations);
    }

    public FindCategoryRounds findCategoryRoundsUseCase() {
        return new FindCategoryRoundsUseCase(competitions, rounds);
    }

    public FindRunResult findRunResultUseCase() {
        return new FindRunResultUseCase(runResults);
    }

    public FindAppeal findAppealUseCase() {
        return new FindAppealUseCase(appeals);
    }

    public GetStandings getStandingsUseCase() {
        return new GetStandingsUseCase(standings);
    }

    public FindAuditTrail findAuditTrailUseCase() {
        return new FindAuditTrailUseCase(auditLog);
    }
}
