package com.dps.roboleague.config;

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
import com.dps.roboleague.enrollment.application.FindCategoryTeamsUseCase;
import com.dps.roboleague.enrollment.application.FindCompetitionUseCase;
import com.dps.roboleague.enrollment.application.FindTeamRegistrationUseCase;
import com.dps.roboleague.enrollment.application.FindTeamRegistrationsUseCase;
import com.dps.roboleague.enrollment.application.RegisterTeamUseCase;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.competition.SeasonRepository;
import com.dps.roboleague.enrollment.eligibility.EligibilityRequirementsSource;
import com.dps.roboleague.enrollment.port.in.CreateSeason;
import com.dps.roboleague.enrollment.port.in.FindCategoryTeams;
import com.dps.roboleague.enrollment.port.in.FindCompetition;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistration;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistrations;
import com.dps.roboleague.enrollment.port.in.RegisterTeam;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.results.application.CalculateRunScoreUseCase;
import com.dps.roboleague.results.application.CaptureRunResultUseCase;
import com.dps.roboleague.results.application.CorrectResultUseCase;
import com.dps.roboleague.results.application.FindRunResultUseCase;
import com.dps.roboleague.results.application.FindRoundRunsUseCase;
import com.dps.roboleague.results.application.FindTeamRunsUseCase;
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
import com.dps.roboleague.results.port.in.FindRoundRuns;
import com.dps.roboleague.results.port.in.FindTeamRuns;
import com.dps.roboleague.results.port.in.GenerateStandings;
import com.dps.roboleague.results.port.in.GetStandings;
import com.dps.roboleague.results.port.in.PublishStandings;
import com.dps.roboleague.results.port.in.RecalculateStandings;
import com.dps.roboleague.results.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.results.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.results.ranking.CategoryScoringService;
import com.dps.roboleague.results.ranking.PendingAppeals;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UseCasesConfig {

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
    @Bean EligibilityRequirementsSource eligibilityRequirements(RulebookRepository rulebooks) {
        return new RulebookEligibilityRequirements(rulebooks);
    }
    @Bean PendingAppeals pendingAppeals(AppealRepository appeals) {
        return new AppealsPendingOnRuns(appeals);
    }
    @Bean RegisterTeam registerTeam(CompetitionRepository competitions, EligibilityRequirementsSource requirements,
            TeamRegistrationRepository registrations, IdGenerator ids, AuditLog audit, Clock clock) {
        return new RegisterTeamUseCase(competitions, requirements, registrations, ids, audit, clock);
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
            PendingAppeals appeals, AuditLog audit, Clock clock) {
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
    @Bean ResultCorrector resultCorrector(RoundRepository rounds, RulebookRepository rulebooks,
            RunResultRepository results, StandingsRepository standings, RecalculateStandings recalculate,
            AuditLog audit, Clock clock) {
        return new ResultCorrector(rounds, rulebooks, results, standings, recalculate, audit, clock);
    }
    @Bean AcceptAppeal acceptAppeal(AppealRepository appeals, RunResultRepository results,
            ResultCorrector corrector, AuditLog audit, Clock clock) {
        return new AcceptAppealUseCase(appeals, results, corrector, audit, clock);
    }
    @Bean CorrectResult correctResult(RunResultRepository results, StandingsRepository standings,
            ResultCorrector corrector) {
        return new CorrectResultUseCase(results, standings, corrector);
    }
    @Bean RejectAppeal rejectAppeal(AppealRepository appeals, AuditLog audit, Clock clock) {
        return new RejectAppealUseCase(appeals, audit, clock);
    }
    @Bean CalculateRunScore calculateRunScore(RunResultRepository results, RoundRepository rounds,
            CategoryScoringService scoring) { return new CalculateRunScoreUseCase(results, rounds, scoring); }
    @Bean FindCompetition findCompetition(CompetitionRepository repository) { return new FindCompetitionUseCase(repository); }
    @Bean FindTeamRegistration findTeamRegistration(TeamRegistrationRepository repository) { return new FindTeamRegistrationUseCase(repository); }
    @Bean FindTeamRegistrations findTeamRegistrations(TeamRegistrationRepository repository) {
        return new FindTeamRegistrationsUseCase(repository);
    }
    @Bean FindRound findRound(RoundRepository repository) { return new FindRoundUseCase(repository); }
    @Bean FindCategoryTeams findCategoryTeams(CompetitionRepository competitions,
            TeamRegistrationRepository registrations) {
        return new FindCategoryTeamsUseCase(competitions, registrations);
    }
    @Bean FindCategoryRounds findCategoryRounds(CompetitionRepository competitions, RoundRepository rounds) {
        return new FindCategoryRoundsUseCase(competitions, rounds);
    }
    @Bean FindRunResult findRunResult(RunResultRepository repository) { return new FindRunResultUseCase(repository); }
    @Bean FindRoundRuns findRoundRuns(RunResultRepository repository) { return new FindRoundRunsUseCase(repository); }
    @Bean FindTeamRuns findTeamRuns(RunResultRepository repository) { return new FindTeamRunsUseCase(repository); }
    @Bean FindAppeal findAppeal(AppealRepository repository) { return new FindAppealUseCase(repository); }
    @Bean GetStandings getStandings(StandingsRepository repository) { return new GetStandingsUseCase(repository); }
    @Bean FindAuditTrail findAuditTrail(AuditLog auditLog) { return new FindAuditTrailUseCase(auditLog); }
}
