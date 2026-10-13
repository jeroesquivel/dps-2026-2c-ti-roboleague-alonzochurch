package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.adapter.in.web.run.RunResponse;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
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
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.ShowcaseFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Captura y consulta de corridas: {@code POST /runs}, {@code POST /runs/automatic-measurements},
 * {@code POST /runs/panel-evaluations} y {@code GET /runs/{runId}}. Corre contra la aplicación real con
 * los adaptadores en memoria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunCaptureApiTest {

    private static final Actor ACTOR = Actor.of("test-actor");
    private static final ChallengeId RESCUE = RescueEditionFixture.CHALLENGE_ID;
    private static final ChallengeId SHOWCASE = ShowcaseFixture.CHALLENGE_ID;

    private static final String ORDINARY_MEASUREMENTS = "{\"TIME\":70,\"OBJECTIVES\":5,\"ENERGY\":40}";
    private static final String DESIGN_SCORES = "[{\"judgeId\":\"J1\",\"criterion\":\"DESIGN\",\"score\":8},"
            + "{\"judgeId\":\"J2\",\"criterion\":\"DESIGN\",\"score\":7}]";
    private static final String SHOWCASE_MEASUREMENTS = "{\"TIME\":70,\"ACCURACY\":0.8}";
    private static final String SHOWCASE_SCORES = "[{\"judgeId\":\"J1\",\"criterion\":\"CREATIVITY\",\"score\":8},"
            + "{\"judgeId\":\"J1\",\"criterion\":\"EXECUTION\",\"score\":9},"
            + "{\"judgeId\":\"J2\",\"criterion\":\"CREATIVITY\",\"score\":6},"
            + "{\"judgeId\":\"J2\",\"criterion\":\"EXECUTION\",\"score\":7}]";
    private static final String RESTART = "[{\"code\":\"RESTART\",\"occurrences\":1}]";

    @Autowired CreateSeason createSeason;
    @Autowired CreateCompetition createCompetition;
    @Autowired RegisterTeam registerTeam;
    @Autowired ScheduleRound scheduleRound;
    @Autowired GenerateStandings generateStandings;
    @Autowired PublishStandings publishStandings;
    @LocalServerPort int port;

    private record Edition(CompetitionId competitionId, CategoryId categoryId, LocalDate day) {
    }

    // ---- AC-1: captura simple ---------------------------------------------------------------------------

    @Test
    void capturesAnOrdinaryRunInOneOperationAndItsQueryShowsItCompleteWithThePinnedRulebook() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, RESCUE, 1, 9, team);

        ResponseEntity<RunResponse> created = post("/runs", complete(round, team, 1), RunResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        RunResponse run = created.getBody();
        assertEquals("/runs/" + run.runId(), created.getHeaders().getLocation().toString());
        assertEquals(round.value(), run.roundId());
        assertEquals(team.value(), run.teamId());
        assertEquals(RESCUE.value(), run.challengeId());
        assertEquals(1, run.attemptNumber());
        assertEquals("v1", run.rulebookVersion());
        assertEquals("COMPLETE", run.completion().status());
        assertTrue(run.completion().missingSources().isEmpty());
        assertEquals("CAPTURED", run.status());
        assertEquals(2, run.evaluations().size());
        assertEquals("J1", run.evaluations().getFirst().judgeId());
        assertEquals("RESTART", run.currentIncidents().getFirst().code());
        assertEquals("5.000", run.currentMeasurements().get("OBJECTIVES").toPlainString());

        ResponseEntity<RunResponse> found = get("/runs/" + run.runId(), RunResponse.class);
        assertEquals(HttpStatus.OK, found.getStatusCode());
        assertEquals(run, found.getBody());
    }

    // ---- AC-2, AC-3, AC-4: fuentes mixtas en cualquier orden -----------------------------------------

    @Test
    void automaticFirstOpensAPendingRunThatWaitsForTheJudgesAndThePanelCompletesTheSameRun() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, SHOWCASE, 1, 9, team);

        ResponseEntity<RunResponse> first = post("/runs/automatic-measurements", automatic(round, team, 1),
                RunResponse.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        RunResponse pending = first.getBody();
        assertEquals("PENDING", pending.completion().status());
        assertEquals(List.of("JUDGES"), pending.completion().missingSources());
        assertEquals(1, pending.completion().receipts().size());
        assertEquals("AUTOMATIC", pending.completion().receipts().getFirst().source());
        assertEquals("automatic-source", pending.completion().receipts().getFirst().actor());
        assertNotNull(pending.completion().receipts().getFirst().receivedAt());
        assertEquals("/runs/" + pending.runId(), first.getHeaders().getLocation().toString());
        assertEquals(pending, get("/runs/" + pending.runId(), RunResponse.class).getBody());

        ResponseEntity<RunResponse> second = post("/runs/panel-evaluations", panel(round, team, 1),
                RunResponse.class);
        assertEquals(HttpStatus.OK, second.getStatusCode());
        RunResponse complete = second.getBody();
        assertEquals(pending.runId(), complete.runId());
        assertEquals("COMPLETE", complete.completion().status());
        assertTrue(complete.completion().missingSources().isEmpty());
        assertEquals(List.of("AUTOMATIC", "JUDGES"),
                complete.completion().receipts().stream().map(RunResponse.ReceiptResponse::source).sorted().toList());
        assertEquals("head-judge", complete.completion().receipts().stream()
                .filter(receipt -> receipt.source().equals("JUDGES")).findFirst().orElseThrow().actor());
        assertEquals(4, complete.evaluations().size());
        assertEquals("0.800", complete.currentMeasurements().get("ACCURACY").toPlainString());
        assertEquals(complete, get("/runs/" + pending.runId(), RunResponse.class).getBody());
    }

    @Test
    void panelFirstOpensAPendingRunThatWaitsForTheAutomaticSourceAndItCompletesTheSameRun() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, SHOWCASE, 1, 9, team);

        ResponseEntity<RunResponse> first = post("/runs/panel-evaluations", panel(round, team, 1),
                RunResponse.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        RunResponse pending = first.getBody();
        assertEquals("PENDING", pending.completion().status());
        assertEquals(List.of("AUTOMATIC"), pending.completion().missingSources());
        assertEquals(4, pending.evaluations().size());
        assertTrue(pending.currentMeasurements().isEmpty());

        ResponseEntity<RunResponse> second = post("/runs/automatic-measurements", automatic(round, team, 1),
                RunResponse.class);
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals(pending.runId(), second.getBody().runId());
        assertEquals("COMPLETE", second.getBody().completion().status());
        assertEquals(2, second.getBody().completion().receipts().size());
    }

    // ---- AC-5: fuente duplicada ----------------------------------------------------------------------

    @Test
    void rejectsADuplicatedSourceWithAConflictAndKeepsTheOriginalReceipt() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, SHOWCASE, 1, 9, team);
        RunResponse original = post("/runs/automatic-measurements", automatic(round, team, 1), RunResponse.class)
                .getBody();

        ResponseEntity<String> duplicate = post("/runs/automatic-measurements",
                automatic(round, team, 1).replace("automatic-source", "someone-else"), String.class);

        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertTrue(duplicate.getBody().contains("\"code\":\"CONFLICT\""), duplicate.getBody());
        assertEquals(original, get("/runs/" + original.runId(), RunResponse.class).getBody());

        post("/runs/panel-evaluations", panel(round, team, 1), RunResponse.class);
        ResponseEntity<String> afterCompletion = post("/runs/panel-evaluations", panel(round, team, 1),
                String.class);
        assertEquals(HttpStatus.CONFLICT, afterCompletion.getStatusCode());
    }

    // ---- AC-6: intento duplicado o fuera de límite -----------------------------------------------------

    @Test
    void rejectsAnAttemptAlreadyUsedAndAnAttemptAboveTheChallengeLimitWithoutCreatingARun() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, RESCUE, 1, 9, team);
        RunResponse first = post("/runs", complete(round, team, 1), RunResponse.class).getBody();

        ResponseEntity<String> reused = post("/runs", complete(round, team, 1), String.class);
        ResponseEntity<String> aboveLimit = post("/runs", complete(round, team, 3), String.class);

        assertEquals(HttpStatus.CONFLICT, reused.getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, aboveLimit.getStatusCode());
        assertTrue(aboveLimit.getBody().contains("\"code\":\"RULE_VIOLATION\""), aboveLimit.getBody());
        assertEquals(first, get("/runs/" + first.runId(), RunResponse.class).getBody());

        ResponseEntity<RunResponse> second = post("/runs", complete(round, team, 2), RunResponse.class);
        assertEquals(HttpStatus.CREATED, second.getStatusCode(), "the rejected captures did not use attempt 2");
        assertFalse(first.runId().equals(second.getBody().runId()), "a second, independent run");
        assertEquals(first, get("/runs/" + first.runId(), RunResponse.class).getBody());
    }

    // ---- AC-7: datos inválidos -----------------------------------------------------------------------

    @Test
    void rejectsMeasurementsPenaltiesJudgesAndTeamsThatBreakTheRulebookAndLeavesTheAttemptFree() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, RESCUE, 1, 9, team);

        assertRule(post("/runs", complete(round, team, 1)
                .replace("\"TIME\":70", "\"UNKNOWN_METRIC\":70"), String.class), "UNKNOWN_METRIC");
        assertRule(post("/runs", complete(round, team, 1)
                .replace("RESTART", "UNKNOWN_PENALTY"), String.class), "UNKNOWN_PENALTY");
        assertRule(post("/runs", complete(round, team, 1)
                .replace("\"J2\"", "\"J-OUTSIDER\""), String.class), "J-OUTSIDER");
        assertRule(post("/runs", complete(round, team, 1)
                .replace("\"criterion\":\"DESIGN\",\"score\":7", "\"criterion\":\"TIME\",\"score\":7"),
                String.class), "TIME");
        assertRule(post("/runs", complete(round, new TeamId("TEAM-NOT-IN-ROUND"), 1), String.class),
                "TEAM-NOT-IN-ROUND");
        ResponseEntity<String> negative = post("/runs", complete(round, team, 1)
                .replace("\"TIME\":70", "\"TIME\":-1"), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, negative.getStatusCode());
        ResponseEntity<String> scoreOutOfRange = post("/runs", complete(round, team, 1)
                .replace("\"score\":8", "\"score\":11"), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, scoreOutOfRange.getStatusCode());

        assertEquals(HttpStatus.CREATED, post("/runs", complete(round, team, 1), String.class).getStatusCode(),
                "none of the rejected requests used the attempt");
    }

    @Test
    void rejectsAnIncompleteMixedSourceAndTheWrongCaptureMode() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId ordinary = schedule(edition, RESCUE, 1, 9, team);
        RoundId mixed = schedule(edition, SHOWCASE, 2, 10, team);

        assertRule(post("/runs/automatic-measurements", automatic(ordinary, team, 1), String.class), "single operation");
        assertRule(post("/runs/panel-evaluations", panel(ordinary, team, 1), String.class), "not mixed");
        assertRule(post("/runs", complete(mixed, team, 1), String.class), "mixed");
        assertRule(post("/runs/panel-evaluations", panel(mixed, team, 1)
                .replace(",{\"judgeId\":\"J2\",\"criterion\":\"EXECUTION\",\"score\":7}", ""), String.class),
                "J2");
        assertRule(post("/runs/automatic-measurements", automatic(mixed, team, 1)
                .replace("\"ACCURACY\":0.8", "\"CREATIVITY\":3"), String.class), "CREATIVITY");

        assertEquals(HttpStatus.CREATED, post("/runs/panel-evaluations", panel(mixed, team, 1), String.class)
                .getStatusCode(), "the rejected sources did not open a run");
    }

    @Test
    void answersBadRequestForMissingOrMalformedFieldsAndNeverAServerError() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, RESCUE, 1, 9, team);
        String valid = complete(round, team, 1);

        List<String> invalid = List.of(
                valid.replace("\"measurements\":" + ORDINARY_MEASUREMENTS + ",", ""),
                valid.replace("\"evaluations\":" + DESIGN_SCORES + ",", ""),
                valid.replace(",\"incidents\":" + RESTART, ""),
                valid.replace("\"roundId\":\"" + round.value() + "\",", ""),
                valid.replace("\"teamId\":\"" + team.value() + "\",", ""),
                valid.replace("\"attemptNumber\":1,", ""),
                valid.replace("\"attemptNumber\":1", "\"attemptNumber\":0"),
                valid.replace("\"actor\":\"test-actor\"", "\"actor\":\" \""),
                valid.replace(",\"actor\":\"test-actor\"", ""),
                valid.replace("\"score\":8", "\"score\":null"),
                valid.replace("\"judgeId\":\"J1\",", ""),
                valid.replace("\"occurrences\":1", "\"occurrences\":0"),
                valid.replace("\"code\":\"RESTART\",", ""),
                valid.replace("\"TIME\":70", "\"TIME\":null"));
        for (String body : invalid) {
            ResponseEntity<String> response = post("/runs", body, String.class);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), body + " -> " + response.getBody());
            assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
        }
        ResponseEntity<String> malformed = post("/runs", "{", String.class);
        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
        assertTrue(malformed.getBody().contains("\"code\":\"MALFORMED_REQUEST\""), malformed.getBody());
        ResponseEntity<String> textAsNumber = post("/runs", valid.replace("\"TIME\":70", "\"TIME\":\"fast\""),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, textAsNumber.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post("/runs/automatic-measurements", "{}", String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post("/runs/panel-evaluations", "{}", String.class).getStatusCode());

        assertEquals(HttpStatus.CREATED, post("/runs", valid, String.class).getStatusCode(),
                "none of the invalid requests used the attempt");
    }

    @Test
    void answersNotFoundForAnUnknownRoundOrRun() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, RESCUE, 1, 9, team);

        ResponseEntity<String> unknownRound = post("/runs", complete(new RoundId("ROUND-UNKNOWN"), team, 1),
                String.class);
        ResponseEntity<String> unknownRun = get("/runs/RUN-UNKNOWN", String.class);

        assertEquals(HttpStatus.NOT_FOUND, unknownRound.getStatusCode());
        assertTrue(unknownRound.getBody().contains("\"code\":\"NOT_FOUND\""), unknownRound.getBody());
        assertEquals(HttpStatus.NOT_FOUND, unknownRun.getStatusCode());
        assertEquals(HttpStatus.CREATED, post("/runs", complete(round, team, 1), String.class).getStatusCode());
    }

    // ---- AC-8: categoría cerrada ---------------------------------------------------------------------

    @Test
    void refusesEveryCaptureOnceTheCategoryHasFinalStandingsAndSavesNothing() {
        Edition edition = newEdition();
        TeamId team = register(edition);
        RoundId round = schedule(edition, RESCUE, 1, 9, team);
        RoundId mixed = schedule(edition, SHOWCASE, 2, 10, team);
        assertEquals(HttpStatus.CREATED, post("/runs", complete(round, team, 1), String.class).getStatusCode());
        post("/runs/automatic-measurements", automatic(mixed, team, 1), RunResponse.class);
        post("/runs/panel-evaluations", panel(mixed, team, 1), RunResponse.class);
        generateStandings.execute(new GenerateStandings.Command(edition.competitionId(), edition.categoryId(), ACTOR));
        publishStandings.execute(new PublishStandings.Command(edition.competitionId(), edition.categoryId(), ACTOR));

        ResponseEntity<String> ordinary = post("/runs", complete(round, team, 2), String.class);
        ResponseEntity<String> automatic = post("/runs/automatic-measurements", automatic(mixed, team, 2),
                String.class);
        ResponseEntity<String> panel = post("/runs/panel-evaluations", panel(mixed, team, 2), String.class);

        for (ResponseEntity<String> response : List.of(ordinary, automatic, panel)) {
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode(), response.getBody());
            assertTrue(response.getBody().contains("appeal"), response.getBody());
        }
        ResponseEntity<String> next = post("/runs", complete(round, team, 1), String.class);
        assertFalse(next.getStatusCode().is2xxSuccessful());
    }

    // ---- verificaciones y escenarios --------------------------------------------------------------------

    private static void assertRule(ResponseEntity<String> response, String mentions) {
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"code\":\"RULE_VIOLATION\""), response.getBody());
        assertTrue(response.getBody().contains(mentions), response.getBody());
    }

    private static String complete(RoundId round, TeamId team, int attempt) {
        return "{\"roundId\":\"" + round.value() + "\",\"teamId\":\"" + team.value() + "\",\"attemptNumber\":"
                + attempt + ",\"measurements\":" + ORDINARY_MEASUREMENTS + ",\"evaluations\":" + DESIGN_SCORES
                + ",\"incidents\":" + RESTART + ",\"actor\":\"test-actor\"}";
    }

    private static String automatic(RoundId round, TeamId team, int attempt) {
        return "{\"roundId\":\"" + round.value() + "\",\"teamId\":\"" + team.value() + "\",\"attemptNumber\":"
                + attempt + ",\"measurements\":" + SHOWCASE_MEASUREMENTS + ",\"actor\":\"automatic-source\"}";
    }

    private static String panel(RoundId round, TeamId team, int attempt) {
        return "{\"roundId\":\"" + round.value() + "\",\"teamId\":\"" + team.value() + "\",\"attemptNumber\":"
                + attempt + ",\"evaluations\":" + SHOWCASE_SCORES + ",\"incidents\":" + RESTART
                + ",\"actor\":\"head-judge\"}";
    }

    private Edition newEdition() {
        DateRange period = Editions.nextPeriod();
        SeasonId seasonId = createSeason.execute(
                new CreateSeason.Command("Season " + period.start(), period.start().getYear(), period, ACTOR));
        RulebookDraft rulebook = RescueEditionFixture.rulebook(
                List.of(RescueEditionFixture.rescueChallenge(), ShowcaseFixture.mixedShowcase()),
                RescueEditionFixture.attemptAggregation());
        CreateCompetition.Result competition = createCompetition.execute(new CreateCompetition.Command(seasonId,
                "Competition " + period.start(), period,
                List.of(new CreateCompetition.CategoryDraft("Junior", AgeRange.between(12, 17),
                        TeamFixtures.RESCUE_BOT)),
                rulebook, ACTOR));
        return new Edition(competition.competitionId(), competition.firstCategory(), period.start());
    }

    private TeamId register(Edition edition) {
        List<RegisterTeam.MemberDraft> members = TeamFixtures.eligibleMembers().members().stream()
                .map(member -> new RegisterTeam.MemberDraft(member.fullName(), member.birthDate(), member.role()))
                .toList();
        return registerTeam.execute(new RegisterTeam.Command(edition.competitionId(), edition.categoryId(),
                "Delta Bots", members, TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments(), ACTOR))
                .teamId();
    }

    private RoundId schedule(Edition edition, ChallengeId challenge, int ordinal, int hour, TeamId team) {
        ScheduleRound.HeatDraft heat = new ScheduleRound.HeatDraft(team, ArenaId.of("A1"),
                new TimeSlot(edition.day().atTime(hour, 0), Duration.ofMinutes(15)),
                Set.of(JudgeId.of("J1"), JudgeId.of("J2")));
        return scheduleRound.execute(new ScheduleRound.Command(edition.competitionId(), edition.categoryId(),
                challenge, RoundOrdinal.of(ordinal), List.of(heat), ACTOR));
    }

    // ---- HTTP -----------------------------------------------------------------------------------------

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
