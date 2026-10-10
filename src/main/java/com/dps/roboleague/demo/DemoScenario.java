package com.dps.roboleague.demo;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.competition.RobotClass;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
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
import com.dps.roboleague.domain.shared.ChallengeId;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class DemoScenario {

    private static final Actor ORGANISER = Actor.of("organiser");
    private static final Actor SCOREKEEPER = Actor.of("scorekeeper");
    private static final Actor HEAD_JUDGE = Actor.of("head-judge");
    private static final RobotClass RESCUE_BOT = RobotClass.of("RESCUE_BOT");
    private static final LocalDateTime FIRST_HEAT = LocalDateTime.of(2026, 3, 2, 9, 0);

    private final RoboLeagueCompositionRoot module;
    private final Map<TeamId, String> teamNames = new HashMap<>();

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
                        DemoRulebook.rulebook(), ORGANISER));
        CompetitionId competitionId = competition.competitionId();
        CategoryId categoryId = competition.firstCategory();

        TeamId kappa = register(competitionId, categoryId, "Kappa Labs");
        TeamId delta = register(competitionId, categoryId, "Delta Bots");
        TeamId omega = register(competitionId, categoryId, "Omega Crew");
        TeamId sigma = register(competitionId, categoryId, "Sigma Works");
        List<TeamId> teams = List.of(kappa, delta, omega, sigma);

        RoundId rescue = scheduleRound(competitionId, categoryId, DemoRulebook.CHALLENGE_ID, 1, teams);
        RunId kappaRescue = captureRescue(rescue, kappa, "99", 5, "48", List.of(8, 8), List.of());
        captureRescue(rescue, delta, "117", 5, "48", List.of(5, 5), List.of());
        captureRescue(rescue, omega, "105", 5, "55", List.of(7, 7), List.of(IncidentReport.once(DemoRulebook.RESTART)));
        captureRescue(rescue, sigma, "107", 5, "50", List.of(8, 8), List.of(IncidentReport.once(DemoRulebook.RESTART),
                IncidentReport.once(DemoRulebook.OUT_OF_BOUNDS)));

        RoundId sprint = scheduleRound(competitionId, categoryId, DemoRulebook.SPRINT_ID, 2, teams);
        captureSprint(sprint, kappa, "44", 4);
        captureSprint(sprint, delta, "44", 4);
        captureSprint(sprint, omega, "44", 4);
        captureSprint(sprint, sigma, "46", 4);

        capturePrecisionRound(competitionId, categoryId, 3, teams, List.of("0.80", "0.90", "0.95", "0.95"));
        capturePrecisionRound(competitionId, categoryId, 4, teams, List.of("0.65", "0.85", "0.80", "0.85"));
        RunId sigmaWorstPrecision = capturePrecisionRound(competitionId, categoryId, 5, teams,
                List.of("0.65", "0.50", "0.40", "0.60")).get(3);

        printScore(module.calculateRunScoreUseCase().execute(new CalculateRunScore.Command(kappaRescue)));
        module.generateStandingsUseCase().execute(new GenerateStandings.Command(competitionId, categoryId, ORGANISER));
        printStandings("Published standings",
                module.publishStandingsUseCase().execute(new PublishStandings.Command(competitionId, categoryId, ORGANISER)));

        acceptAppeal(sigmaWorstPrecision, sigma);
        printStandings("Standings recalculated by the accepted appeal",
                module.getStandingsUseCase().execute(new GetStandings.Command(competitionId, categoryId)).latest());
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
        teamNames.put(outcome.teamId(), teamName);
        return outcome.teamId();
    }

    private RoundId scheduleRound(CompetitionId competitionId, CategoryId categoryId, ChallengeId challengeId,
            int ordinal, List<TeamId> teams) {
        LocalDateTime start = FIRST_HEAT.plusMinutes(90L * (ordinal - 1));
        List<ScheduleRound.HeatDraft> heats = IntStream.range(0, teams.size())
                .mapToObj(index -> heat(teams.get(index), "A1", start.plusMinutes(20L * index)))
                .toList();
        return module.scheduleRoundUseCase().execute(new ScheduleRound.Command(competitionId, categoryId,
                challengeId, RoundOrdinal.of(ordinal), heats, ORGANISER));
    }

    private ScheduleRound.HeatDraft heat(TeamId teamId, String arena, LocalDateTime start) {
        return new ScheduleRound.HeatDraft(teamId, ArenaId.of(arena), new TimeSlot(start, Duration.ofMinutes(15)),
                Set.of(JudgeId.of("J1"), JudgeId.of("J2")));
    }

    private RunId captureRescue(RoundId roundId, TeamId teamId, String seconds, int objectives, String energy,
            List<Integer> judgeScores, List<IncidentReport> incidents) {
        JudgeEvaluations evaluations = new JudgeEvaluations(IntStream.range(0, judgeScores.size())
                .mapToObj(index -> new JudgeEvaluation(JudgeId.of("J" + (index + 1)), DemoRulebook.DESIGN,
                        JudgeScore.of(judgeScores.get(index).longValue())))
                .toList());
        return capture(roundId, teamId, measurements(seconds, objectives, energy), evaluations, incidents);
    }

    private RunId captureSprint(RoundId roundId, TeamId teamId, String seconds, int checkpoints) {
        return capture(roundId, teamId, MeasurementSet.empty()
                .with(DemoRulebook.TIME, MetricValue.of(seconds))
                .with(DemoRulebook.CHECKPOINTS, MetricValue.of(checkpoints)), new JudgeEvaluations(List.of()),
                List.of());
    }

    private List<RunId> capturePrecisionRound(CompetitionId competitionId, CategoryId categoryId, int ordinal,
            List<TeamId> teams, List<String> accuracies) {
        RoundId roundId = scheduleRound(competitionId, categoryId, DemoRulebook.PRECISION_ID, ordinal, teams);
        return IntStream.range(0, teams.size())
                .mapToObj(index -> capture(roundId, teams.get(index), accuracy(accuracies.get(index)),
                        new JudgeEvaluations(List.of()), List.of()))
                .toList();
    }

    private RunId capture(RoundId roundId, TeamId teamId, MeasurementSet measurements, JudgeEvaluations evaluations,
            List<IncidentReport> incidents) {
        return module.captureRunResultUseCase().execute(new CaptureRunResult.Command(roundId, teamId,
                AttemptNumber.first(), measurements, evaluations, incidents, SCOREKEEPER));
    }

    private MeasurementSet accuracy(String ratio) {
        return MeasurementSet.empty().with(DemoRulebook.ACCURACY, MetricValue.of(ratio));
    }

    private MeasurementSet measurements(String seconds, int objectives, String energy) {
        return MeasurementSet.empty()
                .with(DemoRulebook.TIME, MetricValue.of(seconds))
                .with(DemoRulebook.OBJECTIVES, MetricValue.of(objectives))
                .with(DemoRulebook.ENERGY, MetricValue.of(energy));
    }

    private void acceptAppeal(RunId runId, TeamId teamId) {
        AppealId appealId = module.submitAppealUseCase().execute(new SubmitAppeal.Command(runId, teamId,
                "the target sensor misread two hits", Actor.of("sigma-captain")));
        module.acceptAppealUseCase().execute(new AcceptAppeal.Command(appealId,
                "the video review confirms the hits", accuracy("0.98"), List.of(), HEAD_JUDGE));
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
        standings.entries().forEach(this::printEntry);
    }

    private void printEntry(StandingEntry entry) {
        System.out.printf("  %d. %-12s %8s %s%n", entry.position(), teamNames.get(entry.teamId()),
                entry.totalPoints(), tiebreaksOf(entry));
        entry.explanation().subtotals().forEach(subtotal -> {
            System.out.printf("       %-16s %8s  %s%n", subtotal.code(), subtotal.points(), subtotal.description());
            subtotal.rounds().forEach(round -> System.out.printf("         round %d %8s  %-9s %s%n",
                    round.ordinal().value(), round.points(), round.status(), round.reason()));
        });
    }

    private String tiebreaksOf(StandingEntry entry) {
        return entry.appliedTiebreaks().stream()
                .map(AppliedTiebreak::description)
                .collect(Collectors.joining(", ", "(", ")"));
    }
}
