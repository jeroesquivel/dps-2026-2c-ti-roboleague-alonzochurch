package com.dps.roboleague.support;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.FindAuditTrail;
import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.port.in.PublishRulebook;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.ranking.AttemptAggregation;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluation;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.Identifier;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.Robot;
import com.dps.roboleague.domain.team.TeamDocument;
import com.dps.roboleague.domain.team.TeamMembers;
import com.dps.roboleague.domain.team.TeamRegistration;
import com.dps.roboleague.infrastructure.config.RoboLeagueCompositionRoot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public final class TestEdition {

    public static final Actor ACTOR = Actor.of("test-actor");
    public static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");
    public static final LocalDate FIRST_DAY = LocalDate.of(2026, 3, 1);
    public static final LocalDate LAST_DAY = LocalDate.of(2026, 3, 5);

    private final RoboLeagueCompositionRoot module;
    private final CompetitionId competitionId;
    private final CategoryId categoryId;

    private TestEdition(RoboLeagueCompositionRoot module, CompetitionId competitionId, CategoryId categoryId) {
        this.module = module;
        this.competitionId = competitionId;
        this.categoryId = categoryId;
    }

    public static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    public static TestEdition start() {
        return start(RoboLeagueCompositionRoot.inMemory(fixedClock()));
    }

    public static TestEdition start(RoboLeagueCompositionRoot module) {
        SeasonId seasonId = module.createSeasonUseCase().execute(new CreateSeason.Command("Season 2026", 2026,
                DateRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), ACTOR));
        CreateCompetition.Result competition = module.createCompetitionUseCase()
                .execute(new CreateCompetition.Command(seasonId, "National Open",
                        DateRange.of(FIRST_DAY, LAST_DAY),
                        List.of(new CreateCompetition.CategoryDraft("Junior", AgeRange.between(12, 17),
                                TeamFixtures.RESCUE_BOT)),
                        RescueEditionFixture.rulebook(), ACTOR));
        return new TestEdition(module, competition.competitionId(), competition.firstCategory());
    }

    public RulebookVersion publishRulebookWith(ScoringRule scoringRule) {
        return publishRulebook(RescueEditionFixture.rulebook(
                RescueEditionFixture.challengeScoredBy(List.of(scoringRule))));
    }

    public RulebookVersion publishRulebookWith(AttemptAggregation aggregation) {
        return publishRulebook(RescueEditionFixture.rulebook(RescueEditionFixture.rescueChallenge(), aggregation));
    }

    public RulebookVersion publishRulebook(RulebookDraft rulebook) {
        return module.publishRulebookUseCase().execute(new PublishRulebook.Command(competitionId, rulebook, ACTOR));
    }


    public RegisterTeam.Outcome register(String name, TeamMembers members, Robot robot,
            List<TeamDocument> documents) {
        return module.registerTeamUseCase().execute(new RegisterTeam.Command(competitionId, categoryId, name,
                draftsOf(members), robot, documents, ACTOR));
    }

    public static List<RegisterTeam.MemberDraft> draftsOf(TeamMembers members) {
        return members.members().stream()
                .map(member -> new RegisterTeam.MemberDraft(member.fullName(), member.birthDate(), member.role()))
                .toList();
    }

    public TeamId registerEligibleTeam(String name) {
        return register(name, TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot(),
                TeamFixtures.completeDocuments()).teamId();
    }

    public RoundId scheduleRound(int ordinal, List<ScheduleRound.HeatDraft> heats) {
        return module.scheduleRoundUseCase().execute(new ScheduleRound.Command(competitionId, categoryId,
                RescueEditionFixture.CHALLENGE_ID, RoundOrdinal.of(ordinal), heats, ACTOR));
    }

    public RoundId scheduleRoundFor(int ordinal, List<TeamId> teams) {
        List<ScheduleRound.HeatDraft> heats = IntStream.range(0, teams.size())
                .mapToObj(index -> heat(teams.get(index), "A1",
                        LocalDateTime.of(2026, 3, 2, 10, 0).plusMinutes(20L * index)))
                .toList();
        return scheduleRound(ordinal, heats);
    }

    public ScheduleRound.HeatDraft heat(TeamId teamId, String arena, LocalDateTime start) {
        return new ScheduleRound.HeatDraft(teamId, ArenaId.of(arena), new TimeSlot(start, Duration.ofMinutes(15)),
                Set.of(JudgeId.of("J1"), JudgeId.of("J2")));
    }

    public RunId capture(RoundId roundId, TeamId teamId, String seconds, int objectives, String energy,
            List<Integer> judgeScores, List<IncidentReport> incidents) {
        return capture(roundId, teamId, 1, seconds, objectives, energy, judgeScores, incidents);
    }

    public RunId capture(RoundId roundId, TeamId teamId, int attempt, String seconds, int objectives, String energy,
            List<Integer> judgeScores, List<IncidentReport> incidents) {
        return module.captureRunResultUseCase().execute(new CaptureRunResult.Command(roundId, teamId,
                AttemptNumber.of(attempt), measurements(seconds, objectives, energy), evaluations(judgeScores),
                incidents, ACTOR));
    }

    public JudgeEvaluations evaluations(List<Integer> judgeScores) {
        return new JudgeEvaluations(IntStream.range(0, judgeScores.size())
                .mapToObj(index -> new JudgeEvaluation(JudgeId.of("J" + (index + 1)), RescueEditionFixture.DESIGN,
                        JudgeScore.of(judgeScores.get(index).longValue())))
                .toList());
    }

    public MeasurementSet measurements(String seconds, int objectives, String energy) {
        return MeasurementSet.empty()
                .with(RescueEditionFixture.TIME, MetricValue.of(seconds))
                .with(RescueEditionFixture.OBJECTIVES, MetricValue.of(objectives))
                .with(RescueEditionFixture.ENERGY, MetricValue.of(energy));
    }


    public RunResult runResult(RunId runId) {
        return module.findRunResultUseCase().execute(runId);
    }

    public TeamRegistration registration(TeamId teamId) {
        return module.findTeamRegistrationUseCase().execute(teamId);
    }

    public Round round(RoundId roundId) {
        return module.findRoundUseCase().execute(roundId);
    }

    public Appeal appeal(AppealId appealId) {
        return module.findAppealUseCase().execute(appealId);
    }

    public Standings latestStandings() {
        return module.getStandingsUseCase().execute(new GetStandings.Command(competitionId, categoryId)).latest();
    }

    public List<Standings> standingsHistory() {
        return module.getStandingsUseCase().execute(new GetStandings.Command(competitionId, categoryId)).history();
    }

    public List<AuditAction> auditActionsFor(Identifier subject) {
        return actionsOf(
                module.findAuditTrailUseCase().execute(new FindAuditTrail.Command(subject)));
    }

    public static List<AuditAction> actionsOf(List<AuditEvent> events) {
        return events.stream().map(AuditEvent::action).toList();
    }

    public RoboLeagueCompositionRoot module() {
        return module;
    }

    public CompetitionId competitionId() {
        return competitionId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }
}
