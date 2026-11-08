package com.dps.roboleague.adapter.in.graphql;

import com.dps.roboleague.adapter.in.web.Editions;
import com.dps.roboleague.enrollment.competition.RobotClass;
import com.dps.roboleague.enrollment.port.in.CreateSeason;
import com.dps.roboleague.enrollment.port.in.RegisterTeam;
import com.dps.roboleague.enrollment.team.Robot;
import com.dps.roboleague.results.port.in.CaptureRunResult;
import com.dps.roboleague.results.port.in.GenerateStandings;
import com.dps.roboleague.results.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.results.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.port.in.CreateCompetition;
import com.dps.roboleague.rules.port.in.PublishRulebook;
import com.dps.roboleague.rules.rulebook.RulebookDraft;
import com.dps.roboleague.rules.scoring.JudgeEvaluation;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.JudgeScore;
import com.dps.roboleague.scheduling.port.in.ScheduleRound;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AgeRange;
import com.dps.roboleague.shared.ArenaId;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.DateRange;
import com.dps.roboleague.shared.JudgeId;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RoundOrdinal;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.SeasonId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.shared.TimeSlot;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import com.dps.roboleague.support.ShowcaseFixture;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Carga los datos de los tests GraphQL a través de los puertos de entrada, como cualquier otro cliente: una
 * competencia con las categorías Junior y Senior y el reglamento RESCUE, equipos, rondas, corridas y posiciones.
 * Cada edición usa un período propio (ver {@link Editions}), así no choca con las de otros tests del mismo contexto.
 * Los tests lo registran con {@code @Import(GraphQlEditions.class)}.
 */
final class GraphQlEditions {

    static final Actor ORGANIZER = Actor.of("organizer");

    private final CreateSeason createSeason;
    private final CreateCompetition createCompetition;
    private final PublishRulebook publishRulebook;
    private final RegisterTeam registerTeam;
    private final ScheduleRound scheduleRound;
    private final CaptureRunResult captureRunResult;
    private final GenerateStandings generateStandings;
    private final RegisterAutomaticMeasurements registerAutomaticMeasurements;
    private final RegisterPanelEvaluations registerPanelEvaluations;

    GraphQlEditions(CreateSeason createSeason, CreateCompetition createCompetition, PublishRulebook publishRulebook,
            RegisterTeam registerTeam, ScheduleRound scheduleRound, CaptureRunResult captureRunResult,
            GenerateStandings generateStandings, RegisterAutomaticMeasurements registerAutomaticMeasurements,
            RegisterPanelEvaluations registerPanelEvaluations) {
        this.createSeason = createSeason;
        this.createCompetition = createCompetition;
        this.publishRulebook = publishRulebook;
        this.registerTeam = registerTeam;
        this.scheduleRound = scheduleRound;
        this.captureRunResult = captureRunResult;
        this.generateStandings = generateStandings;
        this.registerAutomaticMeasurements = registerAutomaticMeasurements;
        this.registerPanelEvaluations = registerPanelEvaluations;
    }

    record Edition(CompetitionId competitionId, DateRange period, CategoryId junior, CategoryId senior) {
    }

    Edition create(String name) {
        return create(name, RescueEditionFixture.rulebook());
    }

    Edition create(String name, RulebookDraft rulebook) {
        DateRange period = Editions.nextPeriod();
        SeasonId season = createSeason.execute(new CreateSeason.Command("Season " + period.start(),
                period.start().getYear(), period, ORGANIZER));
        CreateCompetition.Result created = createCompetition.execute(new CreateCompetition.Command(season, name,
                period, List.of(
                        new CreateCompetition.CategoryDraft("Junior", AgeRange.between(12, 17), TeamFixtures.RESCUE_BOT),
                        new CreateCompetition.CategoryDraft("Senior", AgeRange.between(18, 30), RobotClass.of("SUMO"))),
                rulebook, ORGANIZER));
        return new Edition(created.competitionId(), period, created.categoryIds().get(0),
                created.categoryIds().get(1));
    }

    TeamId registerEligible(Edition edition, String name) {
        return register(edition, name, TeamFixtures.eligibleRobot());
    }

    /** Un equipo con un robot excedido de peso: la inscripción queda {@code REJECTED}. */
    TeamId registerRejected(Edition edition, String name) {
        return register(edition, name, TeamFixtures.overweightRobot());
    }

    /** Una ronda de RESCUE en el primer día de la edición: un turno de 15 minutos por equipo, cada 20 minutos. */
    RoundId scheduleRound(Edition edition, int ordinal, List<TeamId> teams) {
        return scheduleRound(edition, ordinal, teams, RescueEditionFixture.CHALLENGE_ID);
    }

    RoundId scheduleRound(Edition edition, int ordinal, List<TeamId> teams,
            com.dps.roboleague.shared.ChallengeId challengeId) {
        LocalDateTime start = edition.period().start().atTime(8 + ordinal, 0);
        List<ScheduleRound.HeatDraft> heats = IntStream.range(0, teams.size())
                .mapToObj(index -> new ScheduleRound.HeatDraft(teams.get(index), ArenaId.of("A1"),
                        new TimeSlot(start.plusMinutes(20L * index), Duration.ofMinutes(15)),
                        Set.of(JudgeId.of("J1"), JudgeId.of("J2"))))
                .toList();
        return scheduleRound.execute(new ScheduleRound.Command(edition.competitionId(), edition.junior(),
                challengeId, RoundOrdinal.of(ordinal), heats, ORGANIZER));
    }

    /** Una corrida de RESCUE con tiempo 95.5 s, energía 42 y la nota de diseño 8 y 9; el puntaje varía con los objetivos. */
    RunId capture(RoundId round, TeamId team, int attempt, int objectives) {
        return captureRunResult.execute(new CaptureRunResult.Command(round, team, AttemptNumber.of(attempt),
                MeasurementSet.empty()
                        .with(RescueEditionFixture.TIME, MetricValue.of("95.5"))
                        .with(RescueEditionFixture.OBJECTIVES, MetricValue.of(objectives))
                        .with(RescueEditionFixture.ENERGY, MetricValue.of("42")),
                new JudgeEvaluations(List.of(
                        new JudgeEvaluation(JudgeId.of("J1"), RescueEditionFixture.DESIGN, JudgeScore.of(8)),
                        new JudgeEvaluation(JudgeId.of("J2"), RescueEditionFixture.DESIGN, JudgeScore.of(9)))),
                List.of(), ORGANIZER));
    }

    RunId registerAutomatic(RoundId round, TeamId team, int attempt) {
        return registerAutomaticMeasurements.execute(new RegisterAutomaticMeasurements.Command(round, team,
                AttemptNumber.of(attempt), ShowcaseFixture.exampleMeasurements(), ORGANIZER));
    }

    RunId registerPanel(RoundId round, TeamId team, int attempt) {
        return registerPanelEvaluations.execute(new RegisterPanelEvaluations.Command(round, team,
                AttemptNumber.of(attempt), ShowcaseFixture.exampleEvaluations(), ShowcaseFixture.exampleIncidents(),
                ORGANIZER));
    }

    void generateStandings(Edition edition) {
        generateStandings.execute(new GenerateStandings.Command(edition.competitionId(), edition.junior(), ORGANIZER));
    }

    void publishRulebook(Edition edition, RulebookDraft rulebook) {
        publishRulebook.execute(new PublishRulebook.Command(edition.competitionId(), rulebook, ORGANIZER));
    }

    private TeamId register(Edition edition, String name, Robot robot) {
        List<RegisterTeam.MemberDraft> members = TeamFixtures.eligibleMembers().members().stream()
                .map(member -> new RegisterTeam.MemberDraft(member.fullName(), member.birthDate(), member.role()))
                .toList();
        return registerTeam.execute(new RegisterTeam.Command(edition.competitionId(), edition.junior(), name, members,
                robot, TeamFixtures.completeDocuments(), ORGANIZER)).teamId();
    }
}
