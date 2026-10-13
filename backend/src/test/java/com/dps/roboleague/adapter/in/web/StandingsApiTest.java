package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.adapter.in.web.standings.RevisionsResponse;
import com.dps.roboleague.adapter.in.web.standings.StandingsResponse;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
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
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.ShowcaseFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StandingsApiTest {

    private static final Actor ACTOR = Actor.of("test-actor");
    private static final String ORGANIZER = "{\"actor\":\"organizer\"}";
    private static final ChallengeId RESCUE = RescueEditionFixture.CHALLENGE_ID;

    @Autowired CreateSeason createSeason;
    @Autowired CreateCompetition createCompetition;
    @Autowired RegisterTeam registerTeam;
    @Autowired ScheduleRound scheduleRound;
    @Autowired CaptureRunResult captureRunResult;
    @Autowired RegisterAutomaticMeasurements registerMeasurements;
    @Autowired RegisterPanelEvaluations registerPanel;
    @Autowired SubmitAppeal submitAppeal;
    @Autowired AcceptAppeal acceptAppeal;
    @LocalServerPort int port;

    /** Competencia con su categoría y el primer día del evento. */
    private record Edition(CompetitionId competitionId, CategoryId categoryId, LocalDate day) {

        String path() {
            return "/competitions/" + competitionId.value() + "/categories/" + categoryId.value() + "/standings";
        }
    }

    // ---- AC-1: primera tabla provisional --------------------------------------------------------------

    @Test
    void generatesTheFirstProvisionalRevisionInTheOrderCalculatedByTheDomain() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId delta = register(edition, "Delta Bots");
        TeamId omega = register(edition, "Omega Crew");
        RoundId round = schedule(edition, RESCUE, 1, 9, delta, omega);
        captureDelta(round, delta, 1);
        captureOmega(round, omega);

        ResponseEntity<StandingsResponse> created = post(edition.path(), ORGANIZER, StandingsResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals(edition.path(), created.getHeaders().getLocation().toString());
        StandingsResponse standings = created.getBody();
        assertEquals(edition.competitionId().value(), standings.competitionId());
        assertEquals(edition.categoryId().value(), standings.categoryId());
        assertEquals(1, standings.revision());
        assertEquals("PROVISIONAL", standings.status());
        assertEquals("v1", standings.rulebookVersion());
        assertNotNull(Instant.parse(standings.generatedAt()));
        assertTrue(standings.pendingRuns().isEmpty());
        assertEquals(List.of(omega.value(), delta.value()),
                standings.entries().stream().map(StandingsResponse.EntryResponse::teamId).toList());
        StandingsResponse.EntryResponse first = standings.entries().get(0);
        StandingsResponse.EntryResponse second = standings.entries().get(1);
        assertEquals(1, first.position());
        assertEquals("Omega Crew", first.teamName());
        assertEquals(0, new BigDecimal("71.50").compareTo(first.totalPoints()));
        assertEquals(2, second.position());
        assertEquals("Delta Bots", second.teamName());
        assertEquals(0, new BigDecimal("60.75").compareTo(second.totalPoints()));
    }

    @Test
    void explainsEachTotalWithSubtotalsThatAddUpToIt() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId delta = register(edition, "Delta Bots");
        RoundId round = schedule(edition, RESCUE, 1, 9, delta);
        captureDelta(round, delta, 1);

        StandingsResponse standings = post(edition.path(), ORGANIZER, StandingsResponse.class).getBody();

        StandingsResponse.EntryResponse entry = standings.entries().getFirst();
        assertFalse(entry.explanation().subtotals().isEmpty());
        BigDecimal sum = entry.explanation().subtotals().stream()
                .map(StandingsResponse.SubtotalResponse::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, entry.totalPoints().compareTo(sum));
        assertTrue(entry.explanation().subtotals().stream()
                .allMatch(subtotal -> !subtotal.code().isBlank() && !subtotal.description().isBlank()));
    }

    @Test
    void refusesToGenerateASecondTimeAndPointsToTheRecalculation() {
        Edition edition = editionWithCompleteRescueRound();
        post(edition.path(), ORGANIZER, StandingsResponse.class);

        ResponseEntity<String> again = post(edition.path(), ORGANIZER, String.class);

        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
        assertTrue(again.getBody().contains("\"code\":\"CONFLICT\""), again.getBody());
        assertTrue(again.getBody().contains("recalculation"), again.getBody());
    }

    // ---- AC-2 y AC-3: pendientes ----------------------------------------------------------------------

    @Test
    void listsAnIncompleteRunAsPendingWithoutScoringIt() {
        Edition edition = newEdition(ShowcaseFixture.rulebook(ShowcaseFixture.mixedShowcase()));
        TeamId delta = register(edition, "Delta Bots");
        TeamId omega = register(edition, "Omega Crew");
        RoundId round = schedule(edition, ShowcaseFixture.CHALLENGE_ID, 1, 9, delta, omega);
        automatic(round, delta);
        panel(round, delta);
        RunId pendingRun = automatic(round, omega);

        StandingsResponse standings = post(edition.path(), ORGANIZER, StandingsResponse.class).getBody();

        assertEquals(1, standings.pendingRuns().size());
        StandingsResponse.PendingRunResponse pending = standings.pendingRuns().getFirst();
        assertEquals(pendingRun.value(), pending.runId());
        assertEquals(omega.value(), pending.teamId());
        assertEquals("Omega Crew", pending.teamName());
        assertEquals(round.value(), pending.roundId());
        assertEquals(1, pending.attemptNumber());
        assertEquals(List.of("JUDGES"), pending.missingSources());
        assertEquals(List.of(delta.value()),
                standings.entries().stream().map(StandingsResponse.EntryResponse::teamId).toList());
    }

    @Test
    void refusesToPublishWhileARunIsPendingAndKeepsTheRevisionProvisional() {
        Edition edition = newEdition(ShowcaseFixture.rulebook(ShowcaseFixture.mixedShowcase()));
        TeamId delta = register(edition, "Delta Bots");
        RoundId round = schedule(edition, ShowcaseFixture.CHALLENGE_ID, 1, 9, delta);
        automatic(round, delta);
        post(edition.path(), ORGANIZER, StandingsResponse.class);

        ResponseEntity<String> blocked = post(edition.path() + "/publication", ORGANIZER, String.class);

        assertEquals(HttpStatus.CONFLICT, blocked.getStatusCode());
        assertTrue(blocked.getBody().contains("\"code\":\"CONFLICT\""), blocked.getBody());
        assertTrue(blocked.getBody().contains("pending"), blocked.getBody());
        assertEquals("PROVISIONAL", get(edition.path(), StandingsResponse.class).getBody().status());
    }

    // ---- AC-4 y AC-5: publicación ---------------------------------------------------------------------

    @Test
    void refusesToPublishWhileAnAppealIsPendingAndIdentifiesIt() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId delta = register(edition, "Delta Bots");
        RoundId round = schedule(edition, RESCUE, 1, 9, delta);
        RunId run = captureDelta(round, delta, 1);
        post(edition.path(), ORGANIZER, StandingsResponse.class);
        AppealId appeal = submitAppeal.execute(new SubmitAppeal.Command(run, delta, "the timer started late", ACTOR));

        ResponseEntity<String> blocked = post(edition.path() + "/publication", ORGANIZER, String.class);

        assertEquals(HttpStatus.CONFLICT, blocked.getStatusCode());
        assertTrue(blocked.getBody().contains("\"code\":\"CONFLICT\""), blocked.getBody());
        assertTrue(blocked.getBody().contains(appeal.value()), blocked.getBody());
        assertEquals("PROVISIONAL", get(edition.path(), StandingsResponse.class).getBody().status());
    }

    @Test
    void publishesTheSameRevisionAsFinalAndServesItAsTheLatest() {
        Edition edition = editionWithCompleteRescueRound();
        post(edition.path(), ORGANIZER, StandingsResponse.class);

        ResponseEntity<StandingsResponse> published = post(edition.path() + "/publication", ORGANIZER,
                StandingsResponse.class);

        assertEquals(HttpStatus.OK, published.getStatusCode());
        assertEquals(1, published.getBody().revision());
        assertEquals("FINAL", published.getBody().status());
        StandingsResponse latest = get(edition.path(), StandingsResponse.class).getBody();
        assertEquals(1, latest.revision());
        assertEquals("FINAL", latest.status());
        assertEquals(1, get(edition.path() + "/revisions", RevisionsResponse.class).getBody().revisions().size());
    }

    @Test
    void refusesToPublishAFinalRevisionAgain() {
        Edition edition = editionWithCompleteRescueRound();
        post(edition.path(), ORGANIZER, StandingsResponse.class);
        post(edition.path() + "/publication", ORGANIZER, StandingsResponse.class);

        ResponseEntity<String> again = post(edition.path() + "/publication", ORGANIZER, String.class);

        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
        assertTrue(again.getBody().contains("already final"), again.getBody());
    }

    // ---- AC-6, FR-5, FR-9: recálculo e historial ------------------------------------------------------

    @Test
    void recalculatesIntoANewProvisionalRevisionAndKeepsTheHistoryUnchanged() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId delta = register(edition, "Delta Bots");
        TeamId omega = register(edition, "Omega Crew");
        RoundId round = schedule(edition, RESCUE, 1, 9, delta, omega);
        captureDelta(round, delta, 1);
        captureOmega(round, omega);
        StandingsResponse first = post(edition.path(), ORGANIZER, StandingsResponse.class).getBody();
        captureDelta(round, delta, 2, "80", 5, "30", List.of(10, 10));

        ResponseEntity<StandingsResponse> recalculated = post(edition.path() + "/recalculation",
                "{\"reason\":\"a second attempt was captured\",\"actor\":\"organizer\"}", StandingsResponse.class);

        assertEquals(HttpStatus.CREATED, recalculated.getStatusCode());
        assertEquals(edition.path(), recalculated.getHeaders().getLocation().toString());
        StandingsResponse second = recalculated.getBody();
        assertEquals(2, second.revision());
        assertEquals("PROVISIONAL", second.status());
        assertEquals(delta.value(), second.entries().getFirst().teamId());

        List<StandingsResponse> history = get(edition.path() + "/revisions", RevisionsResponse.class).getBody()
                .revisions();
        assertEquals(List.of(1, 2), history.stream().map(StandingsResponse::revision).toList());
        assertEquals(first, history.get(0));
        assertEquals(second, history.get(1));
        assertEquals(second, get(edition.path(), StandingsResponse.class).getBody());
    }

    @Test
    void keepsEveryRevisionWithItsOwnStatusAfterARecalculationFollowingThePublication() {
        Edition edition = editionWithCompleteRescueRound();
        post(edition.path(), ORGANIZER, StandingsResponse.class);
        post(edition.path() + "/publication", ORGANIZER, StandingsResponse.class);

        post(edition.path() + "/recalculation", "{\"reason\":\"re-check\",\"actor\":\"organizer\"}",
                StandingsResponse.class);

        List<StandingsResponse> history = get(edition.path() + "/revisions", RevisionsResponse.class).getBody()
                .revisions();
        assertEquals(List.of("FINAL", "PROVISIONAL"), history.stream().map(StandingsResponse::status).toList());
        assertEquals("PROVISIONAL", get(edition.path(), StandingsResponse.class).getBody().status());
    }

    @Test
    void anAcceptedAppealCreatesTheNextRevisionWithTheCorrectionApplied() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId delta = register(edition, "Delta Bots");
        RoundId round = schedule(edition, RESCUE, 1, 9, delta);
        RunId run = captureDelta(round, delta, 1);
        StandingsResponse first = post(edition.path(), ORGANIZER, StandingsResponse.class).getBody();
        AppealId appeal = submitAppeal.execute(new SubmitAppeal.Command(run, delta, "the timer started late", ACTOR));

        acceptAppeal.execute(new AcceptAppeal.Command(appeal, "the video review confirms it",
                rescueMeasurements("80", 5, "30"), List.of(), ACTOR));

        List<StandingsResponse> history = get(edition.path() + "/revisions", RevisionsResponse.class).getBody()
                .revisions();
        assertEquals(2, history.size());
        assertEquals(first, history.get(0));
        StandingsResponse corrected = history.get(1);
        assertEquals(2, corrected.revision());
        assertEquals("PROVISIONAL", corrected.status());
        assertTrue(corrected.entries().getFirst().totalPoints().compareTo(first.entries().getFirst().totalPoints()) > 0);
    }

    @Test
    void requiresAReasonToRecalculate() {
        Edition edition = editionWithCompleteRescueRound();
        post(edition.path(), ORGANIZER, StandingsResponse.class);

        for (String body : List.of("{\"actor\":\"organizer\"}", "{\"reason\":\"  \",\"actor\":\"organizer\"}")) {
            ResponseEntity<String> response = post(edition.path() + "/recalculation", body, String.class);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), body);
            assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
        }
        assertEquals(1, get(edition.path() + "/revisions", RevisionsResponse.class).getBody().revisions().size());
    }

    @Test
    void requiresAnActorInEveryOperation() {
        Edition edition = editionWithCompleteRescueRound();

        for (String path : List.of(edition.path(), edition.path() + "/publication")) {
            ResponseEntity<String> response = post(path, "{}", String.class);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), path);
            assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
        }
        assertEquals(HttpStatus.NOT_FOUND, get(edition.path(), String.class).getStatusCode());
    }

    @Test
    void rejectsABodyThatIsNotValidJson() {
        Edition edition = editionWithCompleteRescueRound();

        ResponseEntity<String> response = post(edition.path(), "{not json", String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"MALFORMED_REQUEST\""), response.getBody());
    }

    // ---- AC-7: mejores rondas explicadas --------------------------------------------------------------

    @Test
    void explainsWhichRoundsCountedAndWhichWereDiscarded() {
        Edition edition = newEdition(RescueEditionFixture.rulebook(
                RescueEditionFixture.rescueChallengeCountingBestRounds(BestRounds.of(1, 2))));
        TeamId team = register(edition, "Delta Bots");
        RoundId weaker = schedule(edition, RESCUE, 1, 9, team);
        RoundId stronger = schedule(edition, RESCUE, 2, 11, team);
        captureDelta(weaker, team, 1);
        captureOmega(stronger, team);

        StandingsResponse standings = post(edition.path(), ORGANIZER, StandingsResponse.class).getBody();

        StandingsResponse.EntryResponse entry = standings.entries().getFirst();
        assertEquals(0, new BigDecimal("71.50").compareTo(entry.totalPoints()));
        List<StandingsResponse.RoundResponse> rounds = entry.explanation().subtotals().stream()
                .flatMap(subtotal -> subtotal.rounds().stream())
                .toList();
        assertEquals(2, rounds.size());
        StandingsResponse.RoundResponse counted = rounds.stream()
                .filter(round -> round.status().equals("COUNTED")).findFirst().orElseThrow();
        StandingsResponse.RoundResponse discarded = rounds.stream()
                .filter(round -> round.status().equals("DISCARDED")).findFirst().orElseThrow();
        assertEquals(2, counted.ordinal());
        assertEquals(0, new BigDecimal("71.50").compareTo(counted.points()));
        assertEquals(1, discarded.ordinal());
        assertEquals(0, new BigDecimal("60.75").compareTo(discarded.points()));
        assertFalse(counted.reason().isBlank());
        assertFalse(discarded.reason().isBlank());
        assertTrue(entry.explanation().subtotals().stream()
                .anyMatch(subtotal -> subtotal.points().compareTo(entry.totalPoints()) == 0));
    }

    // ---- AC-8: recursos inexistentes ------------------------------------------------------------------

    @Test
    void reportsACategoryWithoutStandingsAsNotFound() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());

        List<ResponseEntity<String>> responses = List.of(
                get(edition.path(), String.class),
                get(edition.path() + "/revisions", String.class),
                post(edition.path() + "/publication", ORGANIZER, String.class),
                post(edition.path() + "/recalculation", "{\"reason\":\"any\",\"actor\":\"organizer\"}", String.class));

        for (ResponseEntity<String> response : responses) {
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), response.getBody());
            assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
        }
    }

    @Test
    void reportsAnUnknownCompetitionOrCategoryAsNotFoundWhenGenerating() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());

        ResponseEntity<String> unknownCompetition = post("/competitions/COMP-UNKNOWN/categories/"
                + edition.categoryId().value() + "/standings", ORGANIZER, String.class);
        ResponseEntity<String> unknownCategory = post("/competitions/" + edition.competitionId().value()
                + "/categories/CAT-UNKNOWN/standings", ORGANIZER, String.class);

        for (ResponseEntity<String> response : List.of(unknownCompetition, unknownCategory)) {
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), response.getBody());
            assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
        }
    }

    // ---- preparación ---------------------------------------------------------------------------------

    private Edition editionWithCompleteRescueRound() {
        Edition edition = newEdition(RescueEditionFixture.rulebook());
        TeamId delta = register(edition, "Delta Bots");
        TeamId omega = register(edition, "Omega Crew");
        RoundId round = schedule(edition, RESCUE, 1, 9, delta, omega);
        captureDelta(round, delta, 1);
        captureOmega(round, omega);
        return edition;
    }

    private Edition newEdition(RulebookDraft rulebook) {
        DateRange period = Editions.nextPeriod();
        SeasonId seasonId = createSeason.execute(
                new CreateSeason.Command("Season " + period.start(), period.start().getYear(), period, ACTOR));
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

    /** Programa una ronda con un heat de 15 minutos por equipo, cada 20 minutos, a partir de la hora indicada. */
    private RoundId schedule(Edition edition, ChallengeId challenge, int ordinal, int hour, TeamId... teams) {
        List<ScheduleRound.HeatDraft> heats = IntStream.range(0, teams.length)
                .mapToObj(index -> new ScheduleRound.HeatDraft(teams[index], ArenaId.of("A1"),
                        new TimeSlot(edition.day().atTime(hour, 0).plusMinutes(20L * index), Duration.ofMinutes(15)),
                        Set.of(JudgeId.of("J1"), JudgeId.of("J2"))))
                .toList();
        return scheduleRound.execute(new ScheduleRound.Command(edition.competitionId(), edition.categoryId(),
                challenge, RoundOrdinal.of(ordinal), heats, ACTOR));
    }

    /** 60,75 puntos con el reglamento de la fixture de Rescue. */
    private RunId captureDelta(RoundId round, TeamId team, int attempt) {
        return captureDelta(round, team, attempt, "95.5", 4, "42", List.of(8, 9));
    }

    private RunId captureDelta(RoundId round, TeamId team, int attempt, String seconds, int objectives,
            String energy, List<Integer> judgeScores) {
        return captureRunResult.execute(new CaptureRunResult.Command(round, team, AttemptNumber.of(attempt),
                rescueMeasurements(seconds, objectives, energy), rescueEvaluations(judgeScores), List.of(), ACTOR));
    }

    /** 71,50 puntos con el reglamento de la fixture de Rescue (incluye un reinicio penalizado). */
    private RunId captureOmega(RoundId round, TeamId team) {
        return captureRunResult.execute(new CaptureRunResult.Command(round, team, AttemptNumber.first(),
                rescueMeasurements("105", 5, "55"), rescueEvaluations(List.of(7, 7)),
                List.of(IncidentReport.once(RescueEditionFixture.RESTART)), ACTOR));
    }

    private static MeasurementSet rescueMeasurements(String seconds, int objectives, String energy) {
        return MeasurementSet.empty()
                .with(RescueEditionFixture.TIME, MetricValue.of(seconds))
                .with(RescueEditionFixture.OBJECTIVES, MetricValue.of(objectives))
                .with(RescueEditionFixture.ENERGY, MetricValue.of(energy));
    }

    private static JudgeEvaluations rescueEvaluations(List<Integer> judgeScores) {
        return new JudgeEvaluations(IntStream.range(0, judgeScores.size())
                .mapToObj(index -> new JudgeEvaluation(JudgeId.of("J" + (index + 1)), RescueEditionFixture.DESIGN,
                        JudgeScore.of(judgeScores.get(index).longValue())))
                .toList());
    }

    private RunId automatic(RoundId round, TeamId team) {
        return registerMeasurements.execute(new RegisterAutomaticMeasurements.Command(round, team,
                AttemptNumber.first(), ShowcaseFixture.exampleMeasurements(), ACTOR));
    }

    private RunId panel(RoundId round, TeamId team) {
        return registerPanel.execute(new RegisterPanelEvaluations.Command(round, team, AttemptNumber.first(),
                ShowcaseFixture.exampleEvaluations(), ShowcaseFixture.exampleIncidents(), ACTOR));
    }

    // ---- HTTP ----------------------------------------------------------------------------------------

    private <T> ResponseEntity<T> get(String path, Class<T> type) {
        return client().get().uri(path).retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(type);
    }

    private <T> ResponseEntity<T> post(String path, String json, Class<T> type) {
        return client().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json)
                .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(type);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}
