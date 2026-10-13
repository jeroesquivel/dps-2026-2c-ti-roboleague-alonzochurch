package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.adapter.in.web.run.RunScoreResponse;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.PublishRulebook;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.scoring.BonusCap;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluation;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
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
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.ShowcaseFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Consulta del puntaje explicable de una corrida: `GET /runs/{runId}/score`. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScoreApiTest {

    private static final Actor ACTOR = Actor.of("test-actor");
    private static final ChallengeId RESCUE = RescueEditionFixture.CHALLENGE_ID;

    @Autowired CreateSeason createSeason;
    @Autowired CreateCompetition createCompetition;
    @Autowired PublishRulebook publishRulebook;
    @Autowired RegisterTeam registerTeam;
    @Autowired ScheduleRound scheduleRound;
    @Autowired CaptureRunResult captureRunResult;
    @Autowired RegisterAutomaticMeasurements registerMeasurements;
    @Autowired RegisterPanelEvaluations registerPanel;
    @LocalServerPort int port;

    private record Edition(CompetitionId competitionId, CategoryId categoryId, LocalDate day) {
    }

    // ---- AC-1: puntaje simple explicable --------------------------------------------------------------

    @Test
    void explainsASimpleRunWithItsVersionAndContributionsThatAddUpToTheTotal() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId team = register(edition, "Delta Bots");
        RunId run = capture(schedule(edition, RESCUE, 1, 9, team), team, "95.5", 4, "42", List.of(8, 9), List.of());

        RunScoreResponse score = score(run);

        assertEquals(run.value(), score.runId());
        assertEquals(team.value(), score.teamId());
        assertEquals("RESCUE", score.challengeId());
        assertEquals("v1", score.rulebookVersion());
        assertEquals("COMPLETE", score.completionStatus());
        assertTrue(score.missingSources().isEmpty());
        assertAmount("60.75", score.total());
        assertFalse(score.contributions().isEmpty());
        assertTrue(score.contributions().stream().allMatch(contribution -> !contribution.ruleCode().isBlank()
                && !contribution.explanation().isBlank() && contribution.points() != null));
        assertAmount("60.75", sum(score.contributions()));
        assertNull(score.bonusCap());
        assertNull(score.bySource());
    }

    @Test
    void includesTheContributionsThatContributeZero() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId team = register(edition, "Delta Bots");
        RunId run = capture(schedule(edition, RESCUE, 1, 9, team), team, "95.5", 4, "42", List.of(8, 9), List.of());

        RunScoreResponse score = score(run);

        assertTrue(score.contributions().stream().anyMatch(
                contribution -> contribution.points().signum() == 0), score.contributions().toString());
    }

    // ---- AC-2: penalización visible -------------------------------------------------------------------

    @Test
    void showsThePenaltyAsAnIdentifiableDeductionWithItsExplanation() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId team = register(edition, "Omega Crew");
        RunId run = capture(schedule(edition, RESCUE, 1, 9, team), team, "105", 5, "55", List.of(7, 7),
                List.of(IncidentReport.once(RescueEditionFixture.RESTART)));

        RunScoreResponse score = score(run);

        List<RunScoreResponse.ContributionResponse> penalties = score.contributions().stream()
                .filter(contribution -> contribution.kind().equals("PENALTY")).toList();
        assertFalse(penalties.isEmpty());
        assertTrue(penalties.stream().anyMatch(penalty -> penalty.points().signum() < 0
                && penalty.explanation().toLowerCase().contains("restart")), penalties.toString());
        assertTrue(penalties.stream().noneMatch(penalty -> penalty.points().signum() > 0));
        assertAmount("71.50", score.total());
        assertAmount("71.50", sum(score.contributions()));
    }

    // ---- AC-3: bonus cap visible ----------------------------------------------------------------------

    @Test
    void showsTheBonusesObtainedTheCapAndTheTrimWhenTheBonusesExceedTheCap() {
        Edition edition = newEdition(cappedRulebook());
        TeamId team = register(edition, "Delta Bots");
        RunId run = capture(schedule(edition, RESCUE, 1, 9, team), team, "55", 5, "35", List.of(8, 9), List.of());

        RunScoreResponse score = score(run);

        RunScoreResponse.BonusCapResponse cap = score.bonusCap();
        assertNotNull(cap);
        assertAmount("35", cap.obtained());
        assertAmount("25", cap.cap());
        assertAmount("10", cap.trimmed());
        assertAmount("25", cap.applied());
        RunScoreResponse.ContributionResponse trim = score.contributions().stream()
                .filter(contribution -> contribution.ruleCode().equals("BONUS_CAP")).findFirst().orElseThrow();
        assertEquals("BONUS", trim.kind());
        assertAmount("-10", trim.points());
        assertAmount("75", score.total());
        assertAmount("75", sum(score.contributions()));
        assertEquals(3, score.contributions().stream()
                .filter(contribution -> contribution.ruleCode().equals("BONUS")).count());
    }

    @Test
    void reportsTheCapEvenWhenNothingWasTrimmed() {
        Edition edition = newEdition(cappedRulebook());
        TeamId team = register(edition, "Delta Bots");
        RunId run = capture(schedule(edition, RESCUE, 1, 9, team), team, "95.5", 4, "42", List.of(8, 9), List.of());

        RunScoreResponse.BonusCapResponse cap = score(run).bonusCap();

        assertNotNull(cap);
        assertAmount("0", cap.obtained());
        assertAmount("25", cap.cap());
        assertAmount("0", cap.trimmed());
        assertAmount("0", cap.applied());
    }

    // ---- AC-4: fuentes separadas ----------------------------------------------------------------------

    @Test
    void groupsACompleteMixedRunBySourceAndTheSubtotalsRecomposeTheTotal() {
        Edition edition = newEdition(ShowcaseFixture.rulebook(ShowcaseFixture.mixedShowcase()));
        TeamId team = register(edition, "Delta Bots");
        RoundId round = schedule(edition, ShowcaseFixture.CHALLENGE_ID, 1, 9, team);
        RunId run = automatic(round, team);
        panel(round, team);

        RunScoreResponse score = score(run);

        assertEquals("COMPLETE", score.completionStatus());
        assertAmount("57", score.total());
        RunScoreResponse.BySourceResponse bySource = score.bySource();
        assertNotNull(bySource);
        assertEquals(List.of("AUTOMATIC", "JUDGES"),
                bySource.sources().stream().map(RunScoreResponse.SourceResponse::source).toList());
        assertAmount("34", bySource.sources().get(0).subtotal());
        assertAmount("23", bySource.sources().get(1).subtotal());
        assertAmount("0", bySource.acrossSources().subtotal());
        BigDecimal recomposed = bySource.sources().stream().map(RunScoreResponse.SourceResponse::subtotal)
                .reduce(bySource.acrossSources().subtotal(), BigDecimal::add);
        assertAmount("57", recomposed);
        for (RunScoreResponse.SourceResponse source : bySource.sources()) {
            assertAmount(source.subtotal().toPlainString(), sum(source.contributions()));
        }
        assertAmount("57", sum(score.contributions()));
    }

    // ---- AC-5: corrida pendiente ----------------------------------------------------------------------

    @Test
    void doesNotReturnAPartialScoreForAMixedRunWaitingForTheJudges() {
        Edition edition = newEdition(ShowcaseFixture.rulebook(ShowcaseFixture.mixedShowcase()));
        TeamId team = register(edition, "Delta Bots");
        RunId run = automatic(schedule(edition, ShowcaseFixture.CHALLENGE_ID, 1, 9, team), team);

        ResponseEntity<RunScoreResponse> response = get("/runs/" + run.value() + "/score", RunScoreResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertPending(response.getBody(), "JUDGES");
    }

    @Test
    void reportsTheMissingMeasurementsWhenOnlyThePanelHasReported() {
        Edition edition = newEdition(ShowcaseFixture.rulebook(ShowcaseFixture.mixedShowcase()));
        TeamId team = register(edition, "Delta Bots");
        RunId run = panel(schedule(edition, ShowcaseFixture.CHALLENGE_ID, 1, 9, team), team);

        assertPending(score(run), "AUTOMATIC");
    }

    // ---- AC-6: reglamento histórico -------------------------------------------------------------------

    @Test
    void usesTheRulebookVersionPinnedInTheRunEvenAfterANewVersionIsActive() {
        Edition edition = newEdition(RescueEditionFixture.rulebook(RescueEditionFixture.challengeCappingBonuses(
                RescueEditionFixture.scoringRulesWithThreeBonuses(), Optional.empty())));
        TeamId team = register(edition, "Delta Bots");
        RunId earlier = capture(schedule(edition, RESCUE, 1, 9, team), team, "55", 5, "35", List.of(8, 9), List.of());
        publishRulebook.execute(new PublishRulebook.Command(edition.competitionId(), cappedRulebook(), ACTOR));
        RunId later = capture(schedule(edition, RESCUE, 2, 11, team), team, "55", 5, "35", List.of(8, 9), List.of());

        RunScoreResponse first = score(earlier);
        RunScoreResponse second = score(later);

        assertEquals("v1", first.rulebookVersion());
        assertNull(first.bonusCap());
        assertAmount("85", first.total());
        assertEquals("v2", second.rulebookVersion());
        assertAmount("10", second.bonusCap().trimmed());
        assertAmount("75", second.total());
    }

    // ---- FR-9: correcciones ---------------------------------------------------------------------------

    @Test
    void scoresACorrectedRunWithItsCurrentValuesAndKeepsTheOriginalInTheRunDetail() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId team = register(edition, "Delta Bots");
        RunId run = capture(schedule(edition, RESCUE, 1, 9, team), team, "95.5", 4, "42", List.of(8, 9), List.of());
        ResponseEntity<String> appeal = post("/appeals", "{\"runId\":\"" + run.value() + "\",\"teamId\":\""
                + team.value() + "\",\"claim\":\"timer\",\"actor\":\"team\"}");
        String appealId = appeal.getBody().replaceAll(".*\"appealId\":\"([^\"]+)\".*", "$1");

        post("/appeals/" + appealId + "/acceptance",
                "{\"rationale\":\"video\",\"correctedMeasurements\":{\"TIME\":80,\"OBJECTIVES\":5,\"ENERGY\":30},"
                        + "\"correctedIncidents\":[],\"reviewer\":\"reviewer\"}");

        RunScoreResponse score = score(run);
        assertTrue(score.total().compareTo(new BigDecimal("60.75")) > 0);
        assertAmount(score.total().toPlainString(), sum(score.contributions()));
        String detail = get("/runs/" + run.value(), String.class).getBody();
        assertTrue(detail.contains("\"originalMeasurements\":{\"ENERGY\":42.000,\"OBJECTIVES\":4.000,"
                + "\"TIME\":95.500}"), detail);
    }

    // ---- AC-7: corrida inexistente --------------------------------------------------------------------

    @Test
    void answersNotFoundForAnUnknownRun() {
        ResponseEntity<String> response = get("/runs/RUN-UNKNOWN/score", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
    }

    // ---- verificaciones -------------------------------------------------------------------------------

    private void assertPending(RunScoreResponse score, String missing) {
        assertEquals("PENDING", score.completionStatus());
        assertEquals(List.of(missing), score.missingSources());
        assertNull(score.total());
        assertNull(score.contributions());
        assertNull(score.bonusCap());
        assertNull(score.bySource());
        assertEquals("v1", score.rulebookVersion());
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static BigDecimal sum(List<RunScoreResponse.ContributionResponse> contributions) {
        return contributions.stream().map(RunScoreResponse.ContributionResponse::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- preparación ----------------------------------------------------------------------------------

    private static RulebookDraft cappedRulebook() {
        return RescueEditionFixture.rulebook(RescueEditionFixture.challengeCappingBonuses(
                RescueEditionFixture.scoringRulesWithThreeBonuses(), Optional.of(BonusCap.of(25))));
    }

    private Edition newEdition(RulebookDraft rulebook) {
        DateRange period = Editions.nextPeriod();
        SeasonId seasonId = createSeason.execute(
                new CreateSeason.Command("Season " + period.start(), 2026, period, ACTOR));
        CreateCompetition.Result competition = createCompetition.execute(new CreateCompetition.Command(seasonId,
                "Competition " + period.start(), period,
                List.of(new CreateCompetition.CategoryDraft("Junior", AgeRange.between(12, 17),
                        TeamFixtures.RESCUE_BOT)),
                rulebook, ACTOR));
        return new Edition(competition.competitionId(), competition.firstCategory(), period.start());
    }

    private TeamId register(Edition edition, String name) {
        List<RegisterTeam.MemberDraft> members = TeamFixtures.eligibleMembers().members().stream()
                .map(member -> new RegisterTeam.MemberDraft(member.fullName(), member.birthDate(), member.role()))
                .toList();
        return registerTeam.execute(new RegisterTeam.Command(edition.competitionId(), edition.categoryId(), name,
                members, TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments(), ACTOR)).teamId();
    }

    private RoundId schedule(Edition edition, ChallengeId challenge, int ordinal, int hour, TeamId team) {
        ScheduleRound.HeatDraft heat = new ScheduleRound.HeatDraft(team, ArenaId.of("A1"),
                new TimeSlot(edition.day().atTime(hour, 0), Duration.ofMinutes(15)),
                Set.of(JudgeId.of("J1"), JudgeId.of("J2")));
        return scheduleRound.execute(new ScheduleRound.Command(edition.competitionId(), edition.categoryId(),
                challenge, RoundOrdinal.of(ordinal), List.of(heat), ACTOR));
    }

    private RunId capture(RoundId round, TeamId team, String seconds, int objectives, String energy,
            List<Integer> judgeScores, List<IncidentReport> incidents) {
        MeasurementSet measurements = MeasurementSet.empty()
                .with(RescueEditionFixture.TIME, MetricValue.of(seconds))
                .with(RescueEditionFixture.OBJECTIVES, MetricValue.of(objectives))
                .with(RescueEditionFixture.ENERGY, MetricValue.of(energy));
        JudgeEvaluations evaluations = new JudgeEvaluations(List.of(
                new JudgeEvaluation(JudgeId.of("J1"), RescueEditionFixture.DESIGN,
                        JudgeScore.of(judgeScores.get(0).longValue())),
                new JudgeEvaluation(JudgeId.of("J2"), RescueEditionFixture.DESIGN,
                        JudgeScore.of(judgeScores.get(1).longValue()))));
        return captureRunResult.execute(new CaptureRunResult.Command(round, team, AttemptNumber.first(),
                measurements, evaluations, incidents, ACTOR));
    }

    private RunId automatic(RoundId round, TeamId team) {
        return registerMeasurements.execute(new RegisterAutomaticMeasurements.Command(round, team,
                AttemptNumber.first(), ShowcaseFixture.exampleMeasurements(), ACTOR));
    }

    private RunId panel(RoundId round, TeamId team) {
        return registerPanel.execute(new RegisterPanelEvaluations.Command(round, team, AttemptNumber.first(),
                ShowcaseFixture.exampleEvaluations(), ShowcaseFixture.exampleIncidents(), ACTOR));
    }

    // ---- HTTP -----------------------------------------------------------------------------------------

    private RunScoreResponse score(RunId run) {
        ResponseEntity<RunScoreResponse> response = get("/runs/" + run.value() + "/score", RunScoreResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private <T> ResponseEntity<T> get(String path, Class<T> type) {
        return client().get().uri(path).retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(type);
    }

    private ResponseEntity<String> post(String path, String json) {
        return client().post().uri(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(json)
                .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(String.class);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}
