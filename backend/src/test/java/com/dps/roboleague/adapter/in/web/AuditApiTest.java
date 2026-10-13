package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.adapter.in.web.appeal.SubmittedAppealResponse;
import com.dps.roboleague.adapter.in.web.audit.AuditEventResponse;
import com.dps.roboleague.adapter.in.web.audit.AuditTrailResponse;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.scoring.JudgeEvaluation;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditApiTest {

    private static final Actor ACTOR = Actor.of("test-actor");
    private static final String ORGANIZER = "{\"actor\":\"organizer\"}";
    private static final String CLAIM = "the timer started late";
    private static final String RATIONALE = "the video review confirms it";
    private static final String BETTER = """
            {"rationale":"%s","correctedMeasurements":{"TIME":80,"OBJECTIVES":5,"ENERGY":30},\
            "correctedIncidents":[],"reviewer":"reviewer"}""".formatted(RATIONALE);

    @Autowired CreateSeason createSeason;
    @Autowired CreateCompetition createCompetition;
    @Autowired RegisterTeam registerTeam;
    @Autowired ScheduleRound scheduleRound;
    @Autowired CaptureRunResult captureRunResult;
    @LocalServerPort int port;

    /** Una competencia con un equipo y una corrida completa (60,75 puntos) capturada por {@code ACTOR}. */
    private record Scenario(SeasonId season, CompetitionId competition, CategoryId category, TeamId team,
            RoundId round, RunId run, LocalDate day) {

        String standings() {
            return "/competitions/" + competition.value() + "/categories/" + category.value() + "/standings";
        }
    }

    // ---- AC-1: captura de resultado -------------------------------------------------------------------

    @Test
    void showsTheCaptureOfARunWithInstantActorAndRegisteredDetails() {
        Instant before = Instant.now();
        Scenario scenario = scenario();

        AuditTrailResponse trail = trail(scenario.run().value());

        assertEquals(scenario.run().value(), trail.subjectId());
        assertEquals(List.of("RESULT_CAPTURED"), actions(trail));
        AuditEventResponse capture = trail.events().getFirst();
        assertEquals(scenario.run().value(), capture.subjectId());
        assertEquals(ACTOR.name(), capture.actor());
        Instant occurredAt = Instant.parse(capture.occurredAt());
        assertFalse(occurredAt.isBefore(before.minusSeconds(1)));
        assertFalse(occurredAt.isAfter(Instant.now().plusSeconds(1)));
        assertEquals("v1", capture.details().get("rulebook"));
        assertTrue(capture.details().get("measurements").contains("TIME=95.5"), capture.details().toString());
    }

    // ---- FR-1: los sujetos soportados -----------------------------------------------------------------

    @Test
    void tracesEverySupportedKindOfSubject() {
        Scenario scenario = scenario();
        post(scenario.standings(), ORGANIZER, String.class);

        assertEquals(List.of("SEASON_CREATED"), actions(trail(scenario.season().value())));
        assertEquals(List.of("COMPETITION_CREATED", "RULEBOOK_PUBLISHED"),
                actions(trail(scenario.competition().value())));
        assertEquals(List.of("TEAM_REGISTERED"), actions(trail(scenario.team().value())));
        assertEquals(List.of("ROUND_SCHEDULED"), actions(trail(scenario.round().value())));
        assertEquals(List.of("STANDINGS_GENERATED"), actions(trail(scenario.category().value())));
        AuditEventResponse generated = trail(scenario.category().value()).events().getFirst();
        assertEquals("organizer", generated.actor());
        assertNotNull(generated.details().get("rulebook"));
    }

    // ---- AC-2: apelación aceptada ---------------------------------------------------------------------

    @Test
    void tracesAnAcceptedAppealAndTheCorrectionItCausedKeepingOriginalAndCurrentValues() {
        Scenario scenario = scenario();
        String appealId = submit(scenario);
        post("/appeals/" + appealId + "/acceptance", BETTER, String.class);

        AuditTrailResponse appealTrail = trail(appealId);
        AuditTrailResponse runTrail = trail(scenario.run().value());

        assertEquals(List.of("APPEAL_SUBMITTED", "APPEAL_RESOLVED"), actions(appealTrail));
        AuditEventResponse submitted = appealTrail.events().get(0);
        assertEquals("team", submitted.actor());
        assertEquals(scenario.run().value(), submitted.details().get("run"));
        AuditEventResponse resolved = appealTrail.events().get(1);
        assertEquals("reviewer", resolved.actor());
        assertEquals("ACCEPTED", resolved.details().get("status"));
        assertEquals(RATIONALE, resolved.details().get("rationale"));
        assertOrdered(appealTrail);

        assertEquals(List.of("RESULT_CAPTURED", "RESULT_CORRECTED"), actions(runTrail));
        AuditEventResponse correction = runTrail.events().get(1);
        assertEquals("reviewer", correction.actor());
        assertTrue(correction.details().get("original").contains("TIME=95.5"), correction.details().toString());
        assertTrue(correction.details().get("corrected").contains("TIME=80"), correction.details().toString());
        assertTrue(correction.details().get("reason").contains(appealId), correction.details().toString());
        assertOrdered(runTrail);
        // la captura original sigue intacta después de la corrección
        assertTrue(runTrail.events().get(0).details().get("measurements").contains("TIME=95.5"));
        // la resolución de la apelación no es posterior a la corrección que ella causó
        assertFalse(Instant.parse(resolved.occurredAt()).isAfter(Instant.parse(correction.occurredAt())));
    }

    // ---- AC-3: apelación rechazada --------------------------------------------------------------------

    @Test
    void tracesARejectedAppealWithoutAnyCorrection() {
        Scenario scenario = scenario();
        String appealId = submit(scenario);
        post("/appeals/" + appealId + "/rejection",
                "{\"rationale\":\"the recording shows a correct start\",\"reviewer\":\"reviewer\"}", String.class);

        AuditTrailResponse appealTrail = trail(appealId);

        assertEquals(List.of("APPEAL_SUBMITTED", "APPEAL_RESOLVED"), actions(appealTrail));
        AuditEventResponse resolved = appealTrail.events().get(1);
        assertEquals("REJECTED", resolved.details().get("status"));
        assertEquals("the recording shows a correct start", resolved.details().get("rationale"));
        assertEquals(List.of("RESULT_CAPTURED"), actions(trail(scenario.run().value())));
    }

    // ---- AC-4: sin eventos ----------------------------------------------------------------------------

    @Test
    void answersAnEmptyListForAWellFormedSubjectWithoutEvents() {
        for (String subject : List.of("RUN-UNKNOWN", "APPEAL-UNKNOWN", "ROUND-UNKNOWN", "COMPETITION-UNKNOWN",
                "CATEGORY-UNKNOWN", "TEAM-UNKNOWN", "SEASON-UNKNOWN")) {
            ResponseEntity<AuditTrailResponse> response = get("/audit?subjectId=" + subject,
                    AuditTrailResponse.class);

            assertEquals(HttpStatus.OK, response.getStatusCode(), subject);
            assertEquals(subject, response.getBody().subjectId());
            assertTrue(response.getBody().events().isEmpty(), subject);
        }
    }

    // ---- formato del identificador --------------------------------------------------------------------

    @Test
    void refusesAMissingOrUnsupportedSubject() {
        for (String query : List.of("", "?subjectId=", "?subjectId=%20%20", "?subjectId=FOO-1", "?subjectId=RUN",
                "?subjectId=RUN-", "?subjectId=-1", "?subjectId=run-1", "?subjectId=HEAT-1")) {
            ResponseEntity<String> response = get("/audit" + query, String.class);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), query);
            assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
            assertFalse(response.getBody().contains("Exception"), response.getBody());
        }
    }

    // ---- AC-5: sólo lectura ---------------------------------------------------------------------------

    @Test
    void offersOnlyReadOperations() {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            ResponseEntity<String> response = client().method(method).uri("/audit?subjectId=RUN-1")
                    .retrieve().onStatus(HttpStatusCode::isError, (request, reply) -> { }).toEntity(String.class);

            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode(), method.name());
        }
    }

    @Test
    void consultingTheTrailDoesNotAddEventsToIt() {
        Scenario scenario = scenario();

        AuditTrailResponse first = trail(scenario.run().value());
        AuditTrailResponse second = trail(scenario.run().value());

        assertEquals(first, second);
    }

    // ---- preparación ----------------------------------------------------------------------------------

    private static List<String> actions(AuditTrailResponse trail) {
        return trail.events().stream().map(AuditEventResponse::action).toList();
    }

    private static void assertOrdered(AuditTrailResponse trail) {
        List<Instant> instants = new ArrayList<>(trail.events().stream()
                .map(event -> Instant.parse(event.occurredAt())).toList());
        assertEquals(instants.stream().sorted().toList(), instants);
    }

    private AuditTrailResponse trail(String subjectId) {
        ResponseEntity<AuditTrailResponse> response = get("/audit?subjectId=" + subjectId, AuditTrailResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), subjectId);
        return response.getBody();
    }

    private String submit(Scenario scenario) {
        ResponseEntity<SubmittedAppealResponse> created = post("/appeals", "{\"runId\":\""
                + scenario.run().value() + "\",\"teamId\":\"" + scenario.team().value() + "\",\"claim\":\"" + CLAIM
                + "\",\"actor\":\"team\"}", SubmittedAppealResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        return created.getBody().appealId();
    }

    private Scenario scenario() {
        DateRange period = Editions.nextPeriod();
        SeasonId seasonId = createSeason.execute(
                new CreateSeason.Command("Season " + period.start(), period.start().getYear(), period, ACTOR));
        CreateCompetition.Result competition = createCompetition.execute(new CreateCompetition.Command(seasonId,
                "Competition " + period.start(), period,
                List.of(new CreateCompetition.CategoryDraft("Junior", AgeRange.between(12, 17),
                        TeamFixtures.RESCUE_BOT)),
                RescueEditionFixture.rulebook(), ACTOR));
        CompetitionId competitionId = competition.competitionId();
        CategoryId categoryId = competition.firstCategory();
        List<RegisterTeam.MemberDraft> members = TeamFixtures.eligibleMembers().members().stream()
                .map(member -> new RegisterTeam.MemberDraft(member.fullName(), member.birthDate(), member.role()))
                .toList();
        TeamId team = registerTeam.execute(new RegisterTeam.Command(competitionId, categoryId, "Delta Bots",
                members, TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments(), ACTOR)).teamId();
        RoundId round = scheduleRound.execute(new ScheduleRound.Command(competitionId, categoryId,
                RescueEditionFixture.CHALLENGE_ID, RoundOrdinal.of(1),
                List.of(new ScheduleRound.HeatDraft(team, ArenaId.of("A1"),
                        new TimeSlot(period.start().atTime(9, 0), Duration.ofMinutes(15)),
                        Set.of(JudgeId.of("J1"), JudgeId.of("J2")))),
                ACTOR));
        RunId run = captureRunResult.execute(new CaptureRunResult.Command(round, team, AttemptNumber.first(),
                MeasurementSet.empty()
                        .with(RescueEditionFixture.TIME, MetricValue.of("95.5"))
                        .with(RescueEditionFixture.OBJECTIVES, MetricValue.of(4))
                        .with(RescueEditionFixture.ENERGY, MetricValue.of("42")),
                new JudgeEvaluations(List.of(
                        new JudgeEvaluation(JudgeId.of("J1"), RescueEditionFixture.DESIGN, JudgeScore.of(8)),
                        new JudgeEvaluation(JudgeId.of("J2"), RescueEditionFixture.DESIGN, JudgeScore.of(9)))),
                List.of(), ACTOR));
        return new Scenario(seasonId, competitionId, categoryId, team, round, run, period.start());
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
