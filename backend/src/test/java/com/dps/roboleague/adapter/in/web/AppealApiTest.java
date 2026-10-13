package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.adapter.in.web.appeal.AppealResponse;
import com.dps.roboleague.adapter.in.web.appeal.SubmittedAppealResponse;
import com.dps.roboleague.adapter.in.web.run.RunResponse;
import com.dps.roboleague.adapter.in.web.run.RunScoreResponse;
import com.dps.roboleague.adapter.in.web.standings.RevisionsResponse;
import com.dps.roboleague.adapter.in.web.standings.StandingsResponse;
import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.appeal.AppealWindow;
import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
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
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.math.BigDecimal;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppealApiTest {

    private static final Actor ACTOR = Actor.of("test-actor");
    private static final String ORGANIZER = "{\"actor\":\"organizer\"}";
    private static final String CLAIM = "the timer started late";
    private static final String RATIONALE = "the video review confirms it";
    /** Mediciones corregidas completas que mejoran el puntaje original (60,75). */
    private static final String BETTER = """
            {"rationale":"%s","correctedMeasurements":{"TIME":80,"OBJECTIVES":5,"ENERGY":30},\
            "correctedIncidents":[],"reviewer":"reviewer"}""".formatted(RATIONALE);

    @Autowired CreateSeason createSeason;
    @Autowired CreateCompetition createCompetition;
    @Autowired RegisterTeam registerTeam;
    @Autowired ScheduleRound scheduleRound;
    @Autowired CaptureRunResult captureRunResult;
    @Autowired AppealRepository appeals;
    @Autowired AuditLog auditLog;
    @LocalServerPort int port;

    private record Edition(CompetitionId competitionId, CategoryId categoryId, LocalDate day) {

        String standings() {
            return "/competitions/" + competitionId.value() + "/categories/" + categoryId.value() + "/standings";
        }
    }

    /** Una corrida completa de Delta Bots (60,75 puntos) y un equipo ajeno de la misma categoría. */
    private record Scenario(Edition edition, TeamId owner, TeamId other, RunId run) {
    }

    // ---- FR-1: consultar antes de reclamar ------------------------------------------------------------

    @Test
    void consultsTheRunAndItsExplainedScoreBeforeAppealing() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());

        RunResponse run = get("/runs/" + scenario.run().value(), RunResponse.class).getBody();
        RunScoreResponse score = get("/runs/" + scenario.run().value() + "/score", RunScoreResponse.class).getBody();

        assertEquals(scenario.run().value(), run.runId());
        assertEquals(scenario.owner().value(), run.teamId());
        assertEquals("CAPTURED", run.status());
        assertEquals("v1", run.rulebookVersion());
        assertEquals(1, run.attemptNumber());
        assertNotNull(run.roundId());
        assertAmount("95.5", run.currentMeasurements().get("TIME"));
        assertAmount("4", run.currentMeasurements().get("OBJECTIVES"));
        assertAmount("42", run.currentMeasurements().get("ENERGY"));
        assertEquals(run.originalMeasurements(), run.currentMeasurements());
        assertTrue(run.currentIncidents().isEmpty());
        assertTrue(run.corrections().isEmpty());
        assertEquals("COMPLETE", run.completion().status());
        assertTrue(run.completion().missingSources().isEmpty());
        assertAmount("60.75", score.total());
        assertEquals("COMPLETE", score.completionStatus());
        assertFalse(score.contributions().isEmpty());
        assertAmount(score.total().toPlainString(), score.contributions().stream()
                .map(RunScoreResponse.ContributionResponse::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void answersNotFoundForAnUnknownRun() {
        for (String path : List.of("/runs/RUN-UNKNOWN", "/runs/RUN-UNKNOWN/score")) {
            ResponseEntity<String> response = get(path, String.class);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), path);
            assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
        }
    }

    // ---- AC-1: presentación válida --------------------------------------------------------------------

    @Test
    void submitsAnAppealAndShowsItAsPending() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());

        ResponseEntity<SubmittedAppealResponse> created = submit(scenario.run(), scenario.owner(), CLAIM,
                SubmittedAppealResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        String appealId = created.getBody().appealId();
        assertEquals("/appeals/" + appealId, created.getHeaders().getLocation().toString());
        AppealResponse appeal = get("/appeals/" + appealId, AppealResponse.class).getBody();
        assertEquals(appealId, appeal.appealId());
        assertEquals(scenario.run().value(), appeal.runId());
        assertEquals(scenario.owner().value(), appeal.teamId());
        assertEquals(CLAIM, appeal.claim());
        assertEquals("SUBMITTED", appeal.status());
        assertNotNull(java.time.Instant.parse(appeal.submittedAt()));
        assertNull(appeal.decision());
    }

    // ---- AC-2: equipo ajeno o fuera de término --------------------------------------------------------

    @Test
    void refusesAnAppealFromATeamThatDoesNotOwnTheRun() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        int auditedBefore = auditLog.findBySubject(scenario.run()).size();

        ResponseEntity<String> response = submit(scenario.run(), scenario.other(), CLAIM, String.class);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"RULE_VIOLATION\""), response.getBody());
        assertNothingFiled(scenario.run(), auditedBefore);
    }

    @Test
    void refusesAnAppealOutsideTheWindowOfTheRulebookFixedInTheRun() {
        RulebookDraft instantWindow = new RulebookDraft(List.of(RescueEditionFixture.rescueChallenge()),
                RescueEditionFixture.eligibilityRequirements(), RescueEditionFixture.attemptAggregation(),
                RescueEditionFixture.tiebreaks(), AppealWindow.of(Duration.ofNanos(1)));
        Scenario scenario = scenario(instantWindow);
        int auditedBefore = auditLog.findBySubject(scenario.run()).size();

        ResponseEntity<String> response = submit(scenario.run(), scenario.owner(), CLAIM, String.class);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"RULE_VIOLATION\""), response.getBody());
        assertTrue(response.getBody().contains("closed"), response.getBody());
        assertNothingFiled(scenario.run(), auditedBefore);
    }

    // ---- AC-3: una apelación por corrida --------------------------------------------------------------

    @Test
    void refusesASecondAppealOnTheSameRunEvenIfTheFirstWasResolved() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        String first = submitOk(scenario);

        ResponseEntity<String> whilePending = submit(scenario.run(), scenario.owner(), "again", String.class);
        post("/appeals/" + first + "/rejection", rejection("no evidence"), AppealResponse.class);
        ResponseEntity<String> afterResolved = submit(scenario.run(), scenario.owner(), "again", String.class);

        for (ResponseEntity<String> response : List.of(whilePending, afterResolved)) {
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertTrue(response.getBody().contains("\"code\":\"CONFLICT\""), response.getBody());
        }
        assertEquals(1, appeals.findByRun(scenario.run()).size());
    }

    // ---- AC-4: aceptación válida ----------------------------------------------------------------------

    @Test
    void acceptsAnAppealCorrectingTheResultAndKeepingTheOriginal() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        String appealId = submitOk(scenario);
        String runPath = "/runs/" + scenario.run().value();

        ResponseEntity<AppealResponse> accepted = post("/appeals/" + appealId + "/acceptance", BETTER,
                AppealResponse.class);

        assertEquals(HttpStatus.OK, accepted.getStatusCode());
        AppealResponse appeal = accepted.getBody();
        assertEquals("ACCEPTED", appeal.status());
        assertEquals("reviewer", appeal.decision().reviewer());
        assertEquals(RATIONALE, appeal.decision().rationale());
        assertNotNull(java.time.Instant.parse(appeal.decision().decidedAt()));
        assertEquals(appeal, get("/appeals/" + appealId, AppealResponse.class).getBody());

        RunResponse run = get(runPath, RunResponse.class).getBody();
        assertEquals("CORRECTED", run.status());
        assertAmount("80", run.currentMeasurements().get("TIME"));
        assertAmount("5", run.currentMeasurements().get("OBJECTIVES"));
        assertAmount("30", run.currentMeasurements().get("ENERGY"));
        assertAmount("95.5", run.originalMeasurements().get("TIME"));
        assertEquals(1, run.corrections().size());
        RunResponse.CorrectionResponse correction = run.corrections().getFirst();
        assertEquals(appealId, correction.sourceAppeal());
        assertEquals("reviewer", correction.actor());
        assertAmount("80", correction.measurements().get("TIME"));
        assertTrue(get(runPath + "/score", RunScoreResponse.class).getBody().total()
                .compareTo(new BigDecimal("60.75")) > 0);

        List<AuditAction> onAppeal = auditLog.findBySubject(AppealId.of(appealId)).stream()
                .map(AuditEvent::action).toList();
        List<AuditAction> onRun = auditLog.findBySubject(scenario.run()).stream().map(AuditEvent::action).toList();
        assertTrue(onAppeal.contains(AuditAction.APPEAL_SUBMITTED), onAppeal.toString());
        assertTrue(onAppeal.contains(AuditAction.APPEAL_RESOLVED), onAppeal.toString());
        assertTrue(onRun.contains(AuditAction.RESULT_CORRECTED), onRun.toString());
    }

    // ---- AC-5: recálculo automático -------------------------------------------------------------------

    @Test
    void recalculatesTheExistingStandingsWhenAnAppealIsAccepted() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        StandingsResponse first = post(scenario.edition().standings(), ORGANIZER, StandingsResponse.class).getBody();
        String appealId = submitOk(scenario);

        post("/appeals/" + appealId + "/acceptance", BETTER, AppealResponse.class);

        List<StandingsResponse> history = get(scenario.edition().standings() + "/revisions",
                RevisionsResponse.class).getBody().revisions();
        assertEquals(2, history.size());
        assertEquals(first, history.get(0));
        StandingsResponse corrected = history.get(1);
        assertEquals(2, corrected.revision());
        assertEquals("PROVISIONAL", corrected.status());
        BigDecimal correctedScore = get("/runs/" + scenario.run().value() + "/score", RunScoreResponse.class)
                .getBody().total();
        assertEquals(0, correctedScore.compareTo(corrected.entries().getFirst().totalPoints()));
        assertTrue(corrected.entries().getFirst().totalPoints()
                .compareTo(first.entries().getFirst().totalPoints()) > 0);
    }

    // ---- AC-6: aceptación sin standings ---------------------------------------------------------------

    @Test
    void correctsTheResultWithoutCreatingStandingsWhenNoneExist() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        String appealId = submitOk(scenario);

        post("/appeals/" + appealId + "/acceptance", BETTER, AppealResponse.class);

        assertEquals("CORRECTED", get("/runs/" + scenario.run().value(), RunResponse.class).getBody().status());
        assertEquals(HttpStatus.NOT_FOUND, get(scenario.edition().standings(), String.class).getStatusCode());
    }

    // ---- AC-7: rechazo --------------------------------------------------------------------------------

    @Test
    void rejectsAnAppealLeavingTheResultAndTheStandingsUntouched() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        StandingsResponse first = post(scenario.edition().standings(), ORGANIZER, StandingsResponse.class).getBody();
        String appealId = submitOk(scenario);
        RunResponse before = get("/runs/" + scenario.run().value(), RunResponse.class).getBody();

        ResponseEntity<AppealResponse> rejected = post("/appeals/" + appealId + "/rejection",
                rejection("the recording shows a correct start"), AppealResponse.class);

        assertEquals(HttpStatus.OK, rejected.getStatusCode());
        assertEquals("REJECTED", rejected.getBody().status());
        assertEquals("reviewer", rejected.getBody().decision().reviewer());
        assertEquals("the recording shows a correct start", rejected.getBody().decision().rationale());
        assertEquals(before, get("/runs/" + scenario.run().value(), RunResponse.class).getBody());
        List<StandingsResponse> history = get(scenario.edition().standings() + "/revisions",
                RevisionsResponse.class).getBody().revisions();
        assertEquals(List.of(first), history);
        assertTrue(auditLog.findBySubject(AppealId.of(appealId)).stream()
                .anyMatch(event -> event.action() == AuditAction.APPEAL_RESOLVED));
    }

    // ---- AC-8: resolución repetida o corrección inválida ----------------------------------------------

    @Test
    void refusesToResolveAnAppealTwiceInAnyCombination() {
        Scenario accepted = scenario(RescueEditionFixture.rulebook());
        String acceptedAppeal = submitOk(accepted);
        post("/appeals/" + acceptedAppeal + "/acceptance", BETTER, AppealResponse.class);
        Scenario rejected = scenario(RescueEditionFixture.rulebook());
        String rejectedAppeal = submitOk(rejected);
        post("/appeals/" + rejectedAppeal + "/rejection", rejection("no evidence"), AppealResponse.class);

        List<ResponseEntity<String>> attempts = List.of(
                post("/appeals/" + acceptedAppeal + "/acceptance", BETTER, String.class),
                post("/appeals/" + acceptedAppeal + "/rejection", rejection("late"), String.class),
                post("/appeals/" + rejectedAppeal + "/acceptance", BETTER, String.class),
                post("/appeals/" + rejectedAppeal + "/rejection", rejection("late"), String.class));

        for (ResponseEntity<String> attempt : attempts) {
            assertEquals(HttpStatus.CONFLICT, attempt.getStatusCode());
            assertTrue(attempt.getBody().contains("\"code\":\"CONFLICT\""), attempt.getBody());
        }
        assertEquals(1, get("/runs/" + accepted.run().value(), RunResponse.class).getBody().corrections().size());
        assertEquals("CAPTURED", get("/runs/" + rejected.run().value(), RunResponse.class).getBody().status());
        assertEquals("ACCEPTED", get("/appeals/" + acceptedAppeal, AppealResponse.class).getBody().status());
        assertEquals("REJECTED", get("/appeals/" + rejectedAppeal, AppealResponse.class).getBody().status());
    }

    @Test
    void refusesACorrectionThatViolatesTheRulebookWithoutPartialEffects() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        StandingsResponse first = post(scenario.edition().standings(), ORGANIZER, StandingsResponse.class).getBody();
        String appealId = submitOk(scenario);
        RunResponse runBefore = get("/runs/" + scenario.run().value(), RunResponse.class).getBody();
        int appealAudit = auditLog.findBySubject(AppealId.of(appealId)).size();
        int runAudit = auditLog.findBySubject(scenario.run()).size();

        List<String> invalid = List.of(
                """
                {"rationale":"r","correctedMeasurements":{"BOGUS":1,"TIME":80,"OBJECTIVES":5,"ENERGY":30},\
                "correctedIncidents":[],"reviewer":"reviewer"}""",
                """
                {"rationale":"r","correctedMeasurements":{"TIME":80,"OBJECTIVES":5,"ENERGY":30},\
                "correctedIncidents":[{"code":"NOT_A_PENALTY","occurrences":1}],"reviewer":"reviewer"}""");
        for (String body : invalid) {
            ResponseEntity<String> response = post("/appeals/" + appealId + "/acceptance", body, String.class);

            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode(), body);
            assertTrue(response.getBody().contains("\"code\":\"RULE_VIOLATION\""), response.getBody());
        }

        assertEquals("SUBMITTED", get("/appeals/" + appealId, AppealResponse.class).getBody().status());
        assertEquals(runBefore, get("/runs/" + scenario.run().value(), RunResponse.class).getBody());
        assertEquals(appealAudit, auditLog.findBySubject(AppealId.of(appealId)).size());
        assertEquals(runAudit, auditLog.findBySubject(scenario.run()).size());
        assertEquals(List.of(first), get(scenario.edition().standings() + "/revisions",
                RevisionsResponse.class).getBody().revisions());
    }

    // ---- AC-9: publicación bloqueada ------------------------------------------------------------------

    @Test
    void blocksPublishingTheStandingsWhileAnAppealIsPendingUntilItIsResolved() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        post(scenario.edition().standings(), ORGANIZER, StandingsResponse.class);
        String appealId = submitOk(scenario);

        ResponseEntity<String> blocked = post(scenario.edition().standings() + "/publication", ORGANIZER,
                String.class);

        assertEquals(HttpStatus.CONFLICT, blocked.getStatusCode());
        assertTrue(blocked.getBody().contains("\"code\":\"CONFLICT\""), blocked.getBody());
        assertEquals("PROVISIONAL", get(scenario.edition().standings(), StandingsResponse.class).getBody().status());

        post("/appeals/" + appealId + "/rejection", rejection("no evidence"), AppealResponse.class);
        ResponseEntity<StandingsResponse> published = post(scenario.edition().standings() + "/publication",
                ORGANIZER, StandingsResponse.class);

        assertEquals(HttpStatus.OK, published.getStatusCode());
        assertEquals("FINAL", published.getBody().status());
    }

    // ---- validación de formato y recursos inexistentes ------------------------------------------------

    @Test
    void validatesTheSubmissionBeforeReachingTheCore() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        String run = scenario.run().value();
        String team = scenario.owner().value();

        List<String> invalid = List.of(
                "{\"runId\":\"" + run + "\",\"teamId\":\"" + team + "\",\"actor\":\"team\"}",
                "{\"runId\":\"" + run + "\",\"teamId\":\"" + team + "\",\"claim\":\"  \",\"actor\":\"team\"}",
                "{\"runId\":\"" + run + "\",\"teamId\":\"" + team + "\",\"claim\":\"x\"}",
                "{\"teamId\":\"" + team + "\",\"claim\":\"x\",\"actor\":\"team\"}",
                "{\"runId\":\"" + run + "\",\"claim\":\"x\",\"actor\":\"team\"}");
        for (String body : invalid) {
            ResponseEntity<String> response = post("/appeals", body, String.class);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), body);
            assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
        }
        assertEquals(HttpStatus.BAD_REQUEST, post("/appeals", "{not json", String.class).getStatusCode());
        assertTrue(appeals.findByRun(scenario.run()).isEmpty());
    }

    @Test
    void validatesTheDecisionsBeforeReachingTheCore() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());
        String appealId = submitOk(scenario);

        List<String> invalidAcceptances = List.of(
                "{\"correctedMeasurements\":{\"TIME\":80},\"correctedIncidents\":[],\"reviewer\":\"r\"}",
                "{\"rationale\":\"  \",\"correctedMeasurements\":{\"TIME\":80},\"correctedIncidents\":[],"
                        + "\"reviewer\":\"r\"}",
                "{\"rationale\":\"x\",\"correctedIncidents\":[],\"reviewer\":\"r\"}",
                "{\"rationale\":\"x\",\"correctedMeasurements\":{\"TIME\":80},\"reviewer\":\"r\"}",
                "{\"rationale\":\"x\",\"correctedMeasurements\":{\"TIME\":80},\"correctedIncidents\":[]}",
                "{\"rationale\":\"x\",\"correctedMeasurements\":{\"TIME\":-1},\"correctedIncidents\":[],"
                        + "\"reviewer\":\"r\"}",
                "{\"rationale\":\"x\",\"correctedMeasurements\":{\"TIME\":null},\"correctedIncidents\":[],"
                        + "\"reviewer\":\"r\"}",
                "{\"rationale\":\"x\",\"correctedMeasurements\":{\"TIME\":80},"
                        + "\"correctedIncidents\":[{\"code\":\"RESTART\"}],\"reviewer\":\"r\"}",
                "{\"rationale\":\"x\",\"correctedMeasurements\":{\"TIME\":80},"
                        + "\"correctedIncidents\":[{\"code\":\"RESTART\",\"occurrences\":0}],\"reviewer\":\"r\"}");
        for (String body : invalidAcceptances) {
            ResponseEntity<String> response = post("/appeals/" + appealId + "/acceptance", body, String.class);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), body);
            assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
        }
        for (String body : List.of("{\"reviewer\":\"r\"}", "{\"rationale\":\" \",\"reviewer\":\"r\"}",
                "{\"rationale\":\"x\"}")) {
            ResponseEntity<String> response = post("/appeals/" + appealId + "/rejection", body, String.class);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), body);
            assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
        }
        assertEquals(HttpStatus.BAD_REQUEST,
                post("/appeals/" + appealId + "/acceptance", "{not json", String.class).getStatusCode());
        assertEquals("SUBMITTED", get("/appeals/" + appealId, AppealResponse.class).getBody().status());
        assertEquals("CAPTURED", get("/runs/" + scenario.run().value(), RunResponse.class).getBody().status());
    }

    @Test
    void answersNotFoundForAnUnknownAppealOrRun() {
        Scenario scenario = scenario(RescueEditionFixture.rulebook());

        List<ResponseEntity<String>> responses = List.of(
                get("/appeals/APPEAL-UNKNOWN", String.class),
                post("/appeals/APPEAL-UNKNOWN/acceptance", BETTER, String.class),
                post("/appeals/APPEAL-UNKNOWN/rejection", rejection("x"), String.class),
                submit(RunId.of("RUN-UNKNOWN"), scenario.owner(), CLAIM, String.class));

        for (ResponseEntity<String> response : responses) {
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
        }
    }

    // ---- preparación ----------------------------------------------------------------------------------

    private void assertNothingFiled(RunId run, int auditedBefore) {
        assertTrue(appeals.findByRun(run).isEmpty());
        assertEquals(auditedBefore, auditLog.findBySubject(run).size());
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static String rejection(String rationale) {
        return "{\"rationale\":\"" + rationale + "\",\"reviewer\":\"reviewer\"}";
    }

    private String submitOk(Scenario scenario) {
        ResponseEntity<SubmittedAppealResponse> created = submit(scenario.run(), scenario.owner(), CLAIM,
                SubmittedAppealResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        return created.getBody().appealId();
    }

    private <T> ResponseEntity<T> submit(RunId run, TeamId team, String claim, Class<T> type) {
        return post("/appeals", "{\"runId\":\"" + run.value() + "\",\"teamId\":\"" + team.value()
                + "\",\"claim\":\"" + claim + "\",\"actor\":\"team\"}", type);
    }

    private Scenario scenario(RulebookDraft rulebook) {
        DateRange period = Editions.nextPeriod();
        SeasonId seasonId = createSeason.execute(
                new CreateSeason.Command("Season " + period.start(), 2026, period, ACTOR));
        CreateCompetition.Result competition = createCompetition.execute(new CreateCompetition.Command(seasonId,
                "Competition " + period.start(), period,
                List.of(new CreateCompetition.CategoryDraft("Junior", AgeRange.between(12, 17),
                        TeamFixtures.RESCUE_BOT)),
                rulebook, ACTOR));
        Edition edition = new Edition(competition.competitionId(), competition.firstCategory(), period.start());
        TeamId owner = register(edition, "Delta Bots");
        TeamId other = register(edition, "Omega Crew");
        List<ScheduleRound.HeatDraft> heats = List.of(owner, other).stream()
                .map(team -> new ScheduleRound.HeatDraft(team, ArenaId.of("A1"),
                        new TimeSlot(edition.day().atTime(9, team.equals(owner) ? 0 : 20), Duration.ofMinutes(15)),
                        Set.of(JudgeId.of("J1"), JudgeId.of("J2"))))
                .toList();
        RoundId round = scheduleRound.execute(new ScheduleRound.Command(edition.competitionId(),
                edition.categoryId(), RescueEditionFixture.CHALLENGE_ID, RoundOrdinal.of(1), heats, ACTOR));
        RunId run = captureRunResult.execute(new CaptureRunResult.Command(round, owner, AttemptNumber.first(),
                MeasurementSet.empty()
                        .with(RescueEditionFixture.TIME, MetricValue.of("95.5"))
                        .with(RescueEditionFixture.OBJECTIVES, MetricValue.of(4))
                        .with(RescueEditionFixture.ENERGY, MetricValue.of("42")),
                new JudgeEvaluations(List.of(
                        new JudgeEvaluation(JudgeId.of("J1"), RescueEditionFixture.DESIGN, JudgeScore.of(8)),
                        new JudgeEvaluation(JudgeId.of("J2"), RescueEditionFixture.DESIGN, JudgeScore.of(9)))),
                List.of(), ACTOR));
        return new Scenario(edition, owner, other, run);
    }

    private TeamId register(Edition edition, String name) {
        List<RegisterTeam.MemberDraft> members = TeamFixtures.eligibleMembers().members().stream()
                .map(member -> new RegisterTeam.MemberDraft(member.fullName(), member.birthDate(), member.role()))
                .toList();
        return registerTeam.execute(new RegisterTeam.Command(edition.competitionId(), edition.categoryId(), name,
                members, TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments(), ACTOR)).teamId();
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
