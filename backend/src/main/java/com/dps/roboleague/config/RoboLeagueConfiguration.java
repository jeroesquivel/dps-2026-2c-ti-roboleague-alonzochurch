package com.dps.roboleague.config;

import com.dps.roboleague.application.usecase.*;
import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.CompetitionRepository;
import com.dps.roboleague.domain.competition.SeasonRepository;
import com.dps.roboleague.domain.port.in.*;
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
import com.dps.roboleague.infrastructure.memory.*;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RoboLeagueConfiguration {

    @Bean SeasonRepository seasonRepository() { return new InMemorySeasonRepository(); }
    @Bean CompetitionRepository competitionRepository() { return new InMemoryCompetitionRepository(); }
    @Bean RulebookRepository rulebookRepository() { return new InMemoryRulebookRepository(); }
    @Bean TeamRegistrationRepository teamRegistrationRepository() { return new InMemoryTeamRegistrationRepository(); }
    @Bean RoundRepository roundRepository() { return new InMemoryRoundRepository(); }
    @Bean RunResultRepository runResultRepository() { return new InMemoryRunResultRepository(); }
    @Bean StandingsRepository standingsRepository() { return new InMemoryStandingsRepository(); }
    @Bean AppealRepository appealRepository() { return new InMemoryAppealRepository(); }
    @Bean AuditLog auditLog() { return new InMemoryAuditLog(); }
    @Bean IdGenerator idGenerator() { return new SequentialIdGenerator(); }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean ScheduleConflictDetector scheduleConflictDetector() { return new ScheduleConflictDetector(); }
    @Bean RankingService rankingService() { return new RankingService(); }
    @Bean CategoryScoringService categoryScoringService(RoundRepository rounds, RunResultRepository results,
            RulebookRepository rulebooks, RankingService ranking) {
        return new CategoryScoringService(rounds, results, rulebooks, ranking);
    }

    @Bean CreateSeason createSeason(SeasonRepository seasons, IdGenerator ids, AuditLog audit, Clock clock) {
        return new CreateSeasonUseCase(seasons, ids, audit, clock);
    }
    @Bean CreateCompetition createCompetition(SeasonRepository seasons, CompetitionRepository competitions,
            RulebookRepository rulebooks, IdGenerator ids, AuditLog audit, Clock clock) {
        return new CreateCompetitionUseCase(seasons, competitions, rulebooks, ids, audit, clock);
    }
    @Bean PublishRulebook publishRulebook(CompetitionRepository competitions, RulebookRepository rulebooks,
            AuditLog audit, Clock clock) {
        return new PublishRulebookUseCase(competitions, rulebooks, audit, clock);
    }
    @Bean RegisterTeam registerTeam(CompetitionRepository competitions, RulebookRepository rulebooks,
            TeamRegistrationRepository registrations, IdGenerator ids, AuditLog audit, Clock clock) {
        return new RegisterTeamUseCase(competitions, rulebooks, registrations, ids, audit, clock);
    }
    @Bean ScheduleRound scheduleRound(CompetitionRepository competitions, RulebookRepository rulebooks,
            TeamRegistrationRepository registrations, RoundRepository rounds, ScheduleConflictDetector conflicts,
            IdGenerator ids, AuditLog audit, Clock clock) {
        return new ScheduleRoundUseCase(competitions, rulebooks, registrations, rounds, conflicts, ids, audit, clock);
    }
    @Bean CaptureRunResult captureRunResult(RoundRepository rounds, RulebookRepository rulebooks,
            RunResultRepository results, StandingsRepository standings, IdGenerator ids, AuditLog audit, Clock clock) {
        return new CaptureRunResultUseCase(rounds, rulebooks, results, standings, ids, audit, clock);
    }
    @Bean RegisterAutomaticMeasurements registerAutomaticMeasurements(RoundRepository rounds,
            RulebookRepository rulebooks, RunResultRepository results, StandingsRepository standings,
            IdGenerator ids, AuditLog audit, Clock clock) {
        return new RegisterAutomaticMeasurementsUseCase(rounds, rulebooks, results, standings, ids, audit, clock);
    }
    @Bean RegisterPanelEvaluations registerPanelEvaluations(RoundRepository rounds, RulebookRepository rulebooks,
            RunResultRepository results, StandingsRepository standings, IdGenerator ids, AuditLog audit, Clock clock) {
        return new RegisterPanelEvaluationsUseCase(rounds, rulebooks, results, standings, ids, audit, clock);
    }
    @Bean GenerateStandings generateStandings(CompetitionRepository competitions, RulebookRepository rulebooks,
            StandingsRepository standings, CategoryScoringService scoring, AuditLog audit, Clock clock) {
        return new GenerateStandingsUseCase(competitions, rulebooks, standings, scoring, audit, clock);
    }
    @Bean PublishStandings publishStandings(StandingsRepository standings, CategoryScoringService scoring,
            AppealRepository appeals, AuditLog audit, Clock clock) {
        return new PublishStandingsUseCase(standings, scoring, appeals, audit, clock);
    }
    @Bean SubmitAppeal submitAppeal(RunResultRepository results, RoundRepository rounds,
            RulebookRepository rulebooks, AppealRepository appeals, IdGenerator ids, AuditLog audit, Clock clock) {
        return new SubmitAppealUseCase(results, rounds, rulebooks, appeals, ids, audit, clock);
    }
    @Bean RecalculateStandings recalculateStandings(StandingsRepository standings, RulebookRepository rulebooks,
            CategoryScoringService scoring, AuditLog audit, Clock clock) {
        return new RecalculateStandingsUseCase(standings, rulebooks, scoring, audit, clock);
    }
    @Bean AcceptAppeal acceptAppeal(AppealRepository appeals, RunResultRepository results, RoundRepository rounds,
            RulebookRepository rulebooks, StandingsRepository standings, RecalculateStandings recalculate,
            AuditLog audit, Clock clock) {
        return new AcceptAppealUseCase(appeals, results, rounds, rulebooks, standings, recalculate, audit, clock);
    }
    @Bean RejectAppeal rejectAppeal(AppealRepository appeals, AuditLog audit, Clock clock) {
        return new RejectAppealUseCase(appeals, audit, clock);
    }
    @Bean CalculateRunScore calculateRunScore(RunResultRepository results, RoundRepository rounds,
            CategoryScoringService scoring) { return new CalculateRunScoreUseCase(results, rounds, scoring); }
    @Bean FindCompetition findCompetition(CompetitionRepository repository) { return new FindCompetitionUseCase(repository); }
    @Bean FindTeamRegistration findTeamRegistration(TeamRegistrationRepository repository) { return new FindTeamRegistrationUseCase(repository); }
    @Bean FindRound findRound(RoundRepository repository) { return new FindRoundUseCase(repository); }
    @Bean FindRunResult findRunResult(RunResultRepository repository) { return new FindRunResultUseCase(repository); }
    @Bean FindAppeal findAppeal(AppealRepository repository) { return new FindAppealUseCase(repository); }
    @Bean GetStandings getStandings(StandingsRepository repository) { return new GetStandingsUseCase(repository); }
    @Bean FindAuditTrail findAuditTrail(AuditLog auditLog) { return new FindAuditTrailUseCase(auditLog); }
}
