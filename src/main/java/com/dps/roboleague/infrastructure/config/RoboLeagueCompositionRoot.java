package com.dps.roboleague.infrastructure.config;

import com.dps.roboleague.application.usecase.AcceptAppealUseCase;
import com.dps.roboleague.application.usecase.CalculateRunScoreUseCase;
import com.dps.roboleague.application.usecase.CaptureRunResultUseCase;
import com.dps.roboleague.application.usecase.CreateCompetitionUseCase;
import com.dps.roboleague.application.usecase.CreateSeasonUseCase;
import com.dps.roboleague.application.usecase.FindAppealUseCase;
import com.dps.roboleague.application.usecase.FindAuditTrailUseCase;
import com.dps.roboleague.application.usecase.FindCompetitionUseCase;
import com.dps.roboleague.application.usecase.FindRoundUseCase;
import com.dps.roboleague.application.usecase.FindRunResultUseCase;
import com.dps.roboleague.application.usecase.FindTeamRegistrationUseCase;
import com.dps.roboleague.application.usecase.GenerateStandingsUseCase;
import com.dps.roboleague.application.usecase.GetStandingsUseCase;
import com.dps.roboleague.application.usecase.PublishRulebookUseCase;
import com.dps.roboleague.application.usecase.PublishStandingsUseCase;
import com.dps.roboleague.application.usecase.RecalculateStandingsUseCase;
import com.dps.roboleague.application.usecase.RegisterAutomaticMeasurementsUseCase;
import com.dps.roboleague.application.usecase.RegisterPanelEvaluationsUseCase;
import com.dps.roboleague.application.usecase.RegisterTeamUseCase;
import com.dps.roboleague.application.usecase.RejectAppealUseCase;
import com.dps.roboleague.application.usecase.ScheduleRoundUseCase;
import com.dps.roboleague.application.usecase.SubmitAppealUseCase;
import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.CompetitionRepository;
import com.dps.roboleague.domain.competition.SeasonRepository;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.FindAppeal;
import com.dps.roboleague.domain.port.in.FindAuditTrail;
import com.dps.roboleague.domain.port.in.FindCompetition;
import com.dps.roboleague.domain.port.in.FindRound;
import com.dps.roboleague.domain.port.in.FindRunResult;
import com.dps.roboleague.domain.port.in.FindTeamRegistration;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.port.in.PublishRulebook;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.RejectAppeal;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.ranking.CategoryScoringService;
import com.dps.roboleague.domain.ranking.RankingService;
import com.dps.roboleague.domain.ranking.StandingsRepository;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.schedule.ScheduleConflictDetector;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.team.TeamRegistrationRepository;
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
        return new RegisterTeamUseCase(competitions, rulebooks, registrations, idGenerator, auditLog, clock);
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
        return new PublishStandingsUseCase(standings, scoringService, appeals, auditLog, clock);
    }

    public SubmitAppeal submitAppealUseCase() {
        return new SubmitAppealUseCase(runResults, rounds, rulebooks, appeals, idGenerator, auditLog, clock);
    }

    public AcceptAppeal acceptAppealUseCase() {
        return new AcceptAppealUseCase(appeals, runResults, rounds, rulebooks, standings,
                recalculateStandingsUseCase(), auditLog, clock);
    }

    public RejectAppeal rejectAppealUseCase() {
        return new RejectAppealUseCase(appeals, auditLog, clock);
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
