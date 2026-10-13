package com.dps.roboleague.demo;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.competition.RobotClass;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluation;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.Dimensions;
import com.dps.roboleague.domain.team.DocumentType;
import com.dps.roboleague.domain.team.MemberRole;
import com.dps.roboleague.domain.team.Robot;
import com.dps.roboleague.domain.team.TeamDocument;
import com.dps.roboleague.domain.team.Weight;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Carga los datos de demostración declarados en {@code application-dev.yaml} usando únicamente los
 * puertos de entrada del núcleo, como lo haría cualquier otro cliente.
 */
final class DevDataSeeder {

    /** Identificadores de lo que se cargó, para registrarlos en el log. */
    record Seeded(CompetitionId competitionId, CategoryId categoryId, Map<String, TeamId> teams,
            Map<Integer, RoundId> rounds) {

        String describe() {
            StringBuilder text = new StringBuilder("Dev seed loaded: competition ").append(competitionId.value())
                    .append(", category ").append(categoryId.value()).append(", teams ");
            teams.forEach((name, id) -> text.append(id.value()).append(" (").append(name).append(") "));
            text.append(", rounds ");
            rounds.forEach((ordinal, id) -> text.append(id.value()).append(" (#").append(ordinal).append(") "));
            return text.toString().trim();
        }
    }

    private static final Weight ROBOT_WEIGHT = Weight.ofKilograms("2.400");
    private static final Dimensions ROBOT_DIMENSIONS = new Dimensions(180, 180, 150);

    private final DevSeedProperties properties;
    private final CreateSeason createSeason;
    private final CreateCompetition createCompetition;
    private final RegisterTeam registerTeam;
    private final ScheduleRound scheduleRound;
    private final CaptureRunResult captureRunResult;
    private final RegisterAutomaticMeasurements registerMeasurements;
    private final RegisterPanelEvaluations registerPanel;

    DevDataSeeder(DevSeedProperties properties, CreateSeason createSeason, CreateCompetition createCompetition,
            RegisterTeam registerTeam, ScheduleRound scheduleRound, CaptureRunResult captureRunResult,
            RegisterAutomaticMeasurements registerMeasurements, RegisterPanelEvaluations registerPanel) {
        this.properties = properties;
        this.createSeason = createSeason;
        this.createCompetition = createCompetition;
        this.registerTeam = registerTeam;
        this.scheduleRound = scheduleRound;
        this.captureRunResult = captureRunResult;
        this.registerMeasurements = registerMeasurements;
        this.registerPanel = registerPanel;
    }

    Seeded seed() {
        Actor actor = Actor.of(properties.actor());
        DevSeedProperties.Season season = properties.season();
        SeasonId seasonId = createSeason.execute(new CreateSeason.Command(season.name(), season.year(),
                DateRange.of(season.start(), season.end()), actor));
        DevSeedProperties.Competition competition = properties.competition();
        DevSeedProperties.Category category = properties.category();
        CreateCompetition.Result created = createCompetition.execute(new CreateCompetition.Command(seasonId,
                competition.name(), DateRange.of(competition.start(), competition.end()),
                List.of(new CreateCompetition.CategoryDraft(category.name(),
                        AgeRange.between(category.minAge(), category.maxAge()),
                        RobotClass.of(category.robotClass()))),
                DemoRulebook.rulebook(), actor));

        Map<String, TeamId> teams = new LinkedHashMap<>();
        properties.teams().forEach(name -> teams.put(name,
                register(created.competitionId(), created.firstCategory(), name, actor)));
        Map<Integer, RoundId> rounds = new LinkedHashMap<>();
        properties.rounds().forEach(round -> rounds.put(round.ordinal(),
                playRound(created, teams, round, actor)));
        return new Seeded(created.competitionId(), created.firstCategory(), teams, rounds);
    }

    private TeamId register(CompetitionId competitionId, CategoryId categoryId, String name, Actor actor) {
        List<RegisterTeam.MemberDraft> members = List.of(
                new RegisterTeam.MemberDraft(name + " captain", LocalDate.of(2010, 5, 20), MemberRole.COMPETITOR),
                new RegisterTeam.MemberDraft(name + " pilot", LocalDate.of(2011, 8, 3), MemberRole.COMPETITOR),
                new RegisterTeam.MemberDraft(name + " coach", LocalDate.of(1988, 2, 10), MemberRole.COACH));
        Robot robot = new Robot(name + " bot", RobotClass.of(properties.category().robotClass()), ROBOT_WEIGHT,
                ROBOT_DIMENSIONS);
        List<TeamDocument> documents = List.of(new TeamDocument(DocumentType.PARENTAL_CONSENT, "PC-" + name),
                new TeamDocument(DocumentType.TECHNICAL_SHEET, "TS-" + name));
        RegisterTeam.Outcome outcome = registerTeam.execute(new RegisterTeam.Command(competitionId, categoryId,
                name, members, robot, documents, actor));
        if (!outcome.isAccepted()) {
            throw new IllegalStateException("the dev seed team " + name + " was not accepted: "
                    + outcome.verdict().reasons());
        }
        return outcome.teamId();
    }

    private RoundId playRound(CreateCompetition.Result competition, Map<String, TeamId> teams,
            DevSeedProperties.Round round, Actor actor) {
        List<TeamId> ids = List.copyOf(teams.values());
        Set<JudgeId> panel = properties.judges().stream().map(JudgeId::of).collect(Collectors.toSet());
        List<ScheduleRound.HeatDraft> heats = IntStream.range(0, ids.size())
                .mapToObj(index -> new ScheduleRound.HeatDraft(ids.get(index), ArenaId.of(properties.arena()),
                        new TimeSlot(round.start().plusMinutes((long) properties.heatSpacingMinutes() * index),
                                Duration.ofMinutes(properties.heatMinutes())),
                        panel))
                .toList();
        RoundId roundId = scheduleRound.execute(new ScheduleRound.Command(competition.competitionId(),
                competition.firstCategory(), ChallengeId.of(round.challenge()), RoundOrdinal.of(round.ordinal()),
                heats, actor));
        round.results().forEach(result -> record(roundId, teamOf(teams, result.team()), result, actor));
        return roundId;
    }

    private void record(RoundId roundId, TeamId teamId, DevSeedProperties.Result result, Actor actor) {
        MeasurementSet measurements = measurementsOf(result.measurements());
        JudgeEvaluations evaluations = evaluationsOf(result.judgeScores());
        List<IncidentReport> incidents = result.incidents().stream()
                .map(code -> IncidentReport.once(PenaltyCode.of(code.toUpperCase(Locale.ROOT))))
                .toList();
        switch (result.source()) {
            case CAPTURE -> captureRunResult.execute(new CaptureRunResult.Command(roundId, teamId,
                    AttemptNumber.first(), measurements, evaluations, incidents, actor));
            case MEASUREMENTS -> registerMeasurements.execute(new RegisterAutomaticMeasurements.Command(roundId,
                    teamId, AttemptNumber.first(), measurements, actor));
            case PANEL -> registerPanel.execute(new RegisterPanelEvaluations.Command(roundId, teamId,
                    AttemptNumber.first(), evaluations, incidents, actor));
        }
    }

    private static TeamId teamOf(Map<String, TeamId> teams, String name) {
        TeamId teamId = teams.get(name);
        if (teamId == null) {
            throw new IllegalStateException("the dev seed has a result for the unknown team " + name);
        }
        return teamId;
    }

    private static MeasurementSet measurementsOf(Map<String, String> values) {
        MeasurementSet measurements = MeasurementSet.empty();
        for (Map.Entry<String, String> value : values.entrySet()) {
            measurements = measurements.with(MetricKey.of(value.getKey().toUpperCase(Locale.ROOT)),
                    MetricValue.of(value.getValue()));
        }
        return measurements;
    }

    /** Cada lista de puntuaciones corresponde a los jueces, en el orden en que se declararon. */
    private JudgeEvaluations evaluationsOf(Map<String, List<Integer>> scores) {
        List<JudgeEvaluation> evaluations = new ArrayList<>();
        scores.forEach((criterion, byJudge) -> {
            if (byJudge.size() != properties.judges().size()) {
                throw new IllegalStateException("criterion " + criterion + " needs one score per judge ("
                        + properties.judges().size() + ") but has " + byJudge.size());
            }
            IntStream.range(0, byJudge.size()).forEach(index -> evaluations.add(new JudgeEvaluation(
                    JudgeId.of(properties.judges().get(index)), MetricKey.of(criterion.toUpperCase(Locale.ROOT)),
                    JudgeScore.of(byJudge.get(index).longValue()))));
        });
        return new JudgeEvaluations(evaluations);
    }
}
