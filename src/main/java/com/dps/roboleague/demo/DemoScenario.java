package com.dps.roboleague.demo;

import com.dps.roboleague.application.port.in.CalculateRunScore;
import com.dps.roboleague.application.port.in.CaptureRunResult;
import com.dps.roboleague.application.port.in.CreateCompetition;
import com.dps.roboleague.application.port.in.CreateSeason;
import com.dps.roboleague.application.port.in.GenerateStandings;
import com.dps.roboleague.application.port.in.PublishRulebook;
import com.dps.roboleague.application.port.in.PublishStandings;
import com.dps.roboleague.application.port.in.RecalculateStandings;
import com.dps.roboleague.application.port.in.RegisterTeam;
import com.dps.roboleague.application.port.in.ResolveAppeal;
import com.dps.roboleague.application.port.in.ScheduleRound;
import com.dps.roboleague.application.port.in.SubmitAppeal;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.competition.RobotClass;
import com.dps.roboleague.domain.ranking.AppliedTiebreak;
import com.dps.roboleague.domain.ranking.StandingEntry;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluation;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.Dimensions;
import com.dps.roboleague.domain.team.DocumentType;
import com.dps.roboleague.domain.team.MemberRole;
import com.dps.roboleague.domain.team.Robot;
import com.dps.roboleague.domain.team.TeamDocument;
import com.dps.roboleague.domain.team.Weight;
import com.dps.roboleague.infrastructure.config.RoboLeagueCompositionRoot;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class DemoScenario {

    private static final Actor ORGANISER = Actor.of("organiser");
    private static final Actor SCOREKEEPER = Actor.of("scorekeeper");
    private static final Actor HEAD_JUDGE = Actor.of("head-judge");
    private static final RobotClass RESCUE_BOT = RobotClass.of("RESCUE_BOT");

    private final RoboLeagueCompositionRoot module;

    public DemoScenario(RoboLeagueCompositionRoot module) {
        this.module = module;
    }

    public void run() {
        SeasonId seasonId = module.createSeasonUseCase().execute(new CreateSeason.Command("Season 2026", 2026,
                DateRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), ORGANISER));
        CreateCompetition.Result competition = module.createCompetitionUseCase()
                .execute(new CreateCompetition.Command(seasonId, "National Open",
                        DateRange.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5)),
                        List.of(new CreateCompetition.CategoryDraft("Junior", AgeRange.between(12, 17), RESCUE_BOT)),
                        ORGANISER));
        CompetitionId competitionId = competition.competitionId();
        CategoryId categoryId = competition.firstCategory();
        module.publishRulebookUseCase().execute(new PublishRulebook.Command(competitionId,
                List.of(DemoRulebook.rescueChallenge()), DemoRulebook.eligibilityPolicy(),
                DemoRulebook.attemptAggregation(), DemoRulebook.tiebreaks(), ORGANISER));

        TeamId delta = register(competitionId, categoryId, "Delta Bots");
        TeamId omega = register(competitionId, categoryId, "Omega Crew");
        RoundId roundId = module.scheduleRoundUseCase().execute(new ScheduleRound.Command(competitionId, categoryId,
                DemoRulebook.CHALLENGE_ID, RoundOrdinal.of(1),
                List.of(heat(delta, "A1", LocalDateTime.of(2026, 3, 2, 10, 0)),
                        heat(omega, "A1", LocalDateTime.of(2026, 3, 2, 10, 20))),
                ORGANISER));

        RunId deltaRun = capture(roundId, delta, "95.5", 4, "42", List.of(8, 9), List.of());
        capture(roundId, omega, "105", 5, "55", List.of(7, 7), List.of(IncidentReport.once(DemoRulebook.RESTART)));

        printScore(module.calculateRunScoreUseCase().execute(new CalculateRunScore.Command(deltaRun)));
        module.generateStandingsUseCase().execute(new GenerateStandings.Command(competitionId, categoryId, ORGANISER));
        printStandings("Published standings",
                module.publishStandingsUseCase().execute(new PublishStandings.Command(competitionId, categoryId, ORGANISER)));

        acceptAppeal(deltaRun, delta);
        printStandings("Standings after the accepted appeal",
                module.recalculateStandingsUseCase().execute(new RecalculateStandings.Command(competitionId, categoryId,
                        "objective granted on appeal", HEAD_JUDGE)));
    }

    private TeamId register(CompetitionId competitionId, CategoryId categoryId, String teamName) {
        List<RegisterTeam.MemberDraft> members = List.of(
                new RegisterTeam.MemberDraft(teamName + " captain", LocalDate.of(2010, 5, 20), MemberRole.COMPETITOR),
                new RegisterTeam.MemberDraft(teamName + " pilot", LocalDate.of(2011, 8, 3), MemberRole.COMPETITOR),
                new RegisterTeam.MemberDraft(teamName + " coach", LocalDate.of(1988, 2, 10), MemberRole.COACH));
        Robot robot = new Robot(teamName + " bot", RESCUE_BOT, Weight.ofKilograms("2.400"),
                new Dimensions(180, 180, 150));
        List<TeamDocument> documents = List.of(
                new TeamDocument(DocumentType.PARENTAL_CONSENT, "PC-" + teamName),
                new TeamDocument(DocumentType.TECHNICAL_SHEET, "TS-" + teamName));
        RegisterTeam.Outcome outcome = module.registerTeamUseCase().execute(new RegisterTeam.Command(competitionId,
                categoryId, teamName, members, robot, documents, ORGANISER));
        System.out.println("Registered " + teamName + " as " + outcome.status());
        return outcome.teamId();
    }

    private ScheduleRound.HeatDraft heat(TeamId teamId, String arena, LocalDateTime start) {
        return new ScheduleRound.HeatDraft(teamId, ArenaId.of(arena), new TimeSlot(start, Duration.ofMinutes(15)),
                Set.of(JudgeId.of("J1"), JudgeId.of("J2")));
    }

    private RunId capture(RoundId roundId, TeamId teamId, String seconds, int objectives, String energy,
            List<Integer> judgeScores, List<IncidentReport> incidents) {
        JudgeEvaluations evaluations = new JudgeEvaluations(IntStream.range(0, judgeScores.size())
                .mapToObj(index -> new JudgeEvaluation(JudgeId.of("J" + (index + 1)), DemoRulebook.DESIGN,
                        JudgeScore.of(judgeScores.get(index).longValue())))
                .toList());
        return module.captureRunResultUseCase().execute(new CaptureRunResult.Command(roundId, teamId,
                AttemptNumber.first(), measurements(seconds, objectives, energy), evaluations, incidents,
                SCOREKEEPER));
    }

    private MeasurementSet measurements(String seconds, int objectives, String energy) {
        return MeasurementSet.empty()
                .with(DemoRulebook.TIME, MetricValue.of(seconds))
                .with(DemoRulebook.OBJECTIVES, MetricValue.of(objectives))
                .with(DemoRulebook.ENERGY, MetricValue.of(energy));
    }

    private void acceptAppeal(RunId runId, TeamId teamId) {
        AppealId appealId = module.submitAppealUseCase().execute(new SubmitAppeal.Command(runId, teamId,
                "the fourth objective was completed before the buzzer", Actor.of("delta-captain")));
        module.resolveAppealUseCase().execute(new ResolveAppeal.Command(appealId, true, HEAD_JUDGE,
                "the video review confirms the objective",
                Optional.of(new ResolveAppeal.Correction(measurements("95.5", 5, "42"), List.of())), HEAD_JUDGE));
    }

    private void printScore(CalculateRunScore.RunScore score) {
        System.out.println();
        System.out.println("Score of run " + score.runId().value() + " under rulebook " + score.rulebookVersion());
        score.breakdown().contributions().forEach(contribution -> System.out.printf("  %-10s %8s  %s%n",
                contribution.ruleCode(), contribution.points(), contribution.explanation()));
        System.out.println("  total " + score.total());
    }

    private void printStandings(String title, Standings standings) {
        System.out.println();
        System.out.println(title + " (revision " + standings.revision() + ", " + standings.status() + ")");
        standings.entries().forEach(entry -> System.out.printf("  %d. %-12s %8s %s%n", entry.position(),
                entry.teamId().value(), entry.totalPoints(), tiebreaksOf(entry)));
    }

    private String tiebreaksOf(StandingEntry entry) {
        return entry.appliedTiebreaks().stream()
                .map(AppliedTiebreak::description)
                .collect(Collectors.joining(", ", "(", ")"));
    }
}
