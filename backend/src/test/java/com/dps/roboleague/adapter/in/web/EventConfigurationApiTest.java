package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.adapter.in.web.audit.AuditEventResponse;
import com.dps.roboleague.adapter.in.web.audit.AuditTrailResponse;
import com.dps.roboleague.adapter.in.web.event.CompetitionCreatedResponse;
import com.dps.roboleague.adapter.in.web.event.CompetitionResponse;
import com.dps.roboleague.adapter.in.web.event.RulebookPublishedResponse;
import com.dps.roboleague.adapter.in.web.event.SeasonCreatedResponse;
import com.dps.roboleague.adapter.in.web.round.RoundResponse;
import com.dps.roboleague.domain.shared.DateRange;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Recorrido completo de la configuración del evento por HTTP con los adaptadores en memoria reales:
 * temporada, competencia con su reglamento inicial, consulta y nuevas versiones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventConfigurationApiTest {

    private static final Pattern COMPETITION_NUMBER = Pattern.compile("COMPETITION-(\\d+)");

    private static final String METRICS = """
            [{"key":"TIME","kind":"TIME_SECONDS","unit":"s","requirement":"REQUIRED"},
             {"key":"OBJECTIVES","kind":"OBJECTIVE_COUNT","unit":"objectives","requirement":"REQUIRED"},
             {"key":"ENERGY","kind":"RESOURCE_UNITS","unit":"mAh","requirement":"REQUIRED"},
             {"key":"DESIGN","kind":"JUDGE_CRITERION","unit":"points","requirement":"OPTIONAL"}]""";
    private static final String TIME_RULE = """
            {"type":"TIME","metric":"TIME","referenceSeconds":120,"pointsPerSecondSaved":0.5,"maximumPoints":30}""";
    private static final String OBJECTIVES_RULE = """
            {"type":"OBJECTIVES","metric":"OBJECTIVES","pointsPerObjective":10,"maximumObjectives":5}""";
    private static final String JUDGES_RULE = """
            {"type":"JUDGES","criterion":"DESIGN","weight":1}""";
    private static final String RESOURCE_RULE = """
            {"type":"RESOURCE","metric":"ENERGY","allowance":50,"pointsPerUnitOver":1}""";
    private static final String BONUS_RULE = """
            {"type":"BONUS","metric":"OBJECTIVES","comparison":"AT_LEAST","threshold":5,"bonusPoints":15}""";
    private static final String PENALTIES = """
            [{"code":"RESTART","description":"manual restart","pointsDeducted":3},
             {"code":"OUT_OF_BOUNDS","description":"robot out of bounds","pointsDeducted":5}]""";
    private static final String RESCUE_RULES = String.join(",", TIME_RULE, OBJECTIVES_RULE, JUDGES_RULE,
            RESOURCE_RULE, BONUS_RULE);
    private static final String ELIGIBILITY = """
            [{"type":"AGE_RANGE"},
             {"type":"TEAM_COMPOSITION","minimumCompetitors":2,"maximumCompetitors":4,"coachRequiredUnderAge":18},
             {"type":"ROBOT_CLASS"},
             {"type":"ROBOT_SPECIFICATION","maximumWeightKg":3,
              "maximumDimensionsMm":{"length":200,"width":200,"height":200}},
             {"type":"REQUIRED_DOCUMENTS","documents":["PARENTAL_CONSENT","TECHNICAL_SHEET"]}]""";
    private static final String TIEBREAKS = """
            [{"type":"HIGHEST_SINGLE_RUN"},{"type":"FEWEST_PENALTIES"},{"type":"FASTEST_METRIC","metric":"TIME"}]""";
    private static final String SHOWCASE = """
            {"id":"SHOWCASE","name":"Showcase",
             "metrics":[{"key":"TIME","kind":"TIME_SECONDS","unit":"s","requirement":"REQUIRED"},
                        {"key":"ACCURACY","kind":"PRECISION_RATIO","unit":"ratio","requirement":"REQUIRED"},
                        {"key":"CREATIVITY","kind":"JUDGE_CRITERION","unit":"points","requirement":"REQUIRED"},
                        {"key":"EXECUTION","kind":"JUDGE_CRITERION","unit":"points","requirement":"REQUIRED"}],
             "scoringRules":[{"type":"TIME","metric":"TIME","referenceSeconds":90,"pointsPerSecondSaved":0.5,
                              "maximumPoints":20},
                             {"type":"PRECISION","metric":"ACCURACY","maximumPoints":30},
                             {"type":"JUDGES","criterion":"CREATIVITY","weight":2},
                             {"type":"JUDGES","criterion":"EXECUTION","weight":1.5}],
             "penalties":[{"code":"RESTART","description":"manual restart","pointsDeducted":3}],
             "maximumAttempts":2,"mixedSources":true}""";
    private static final String CATEGORIES = """
            [{"name":"Junior","minimumAge":12,"maximumAge":17,"robotClass":"RESCUE_BOT"},
             {"name":"Senior","minimumAge":18,"maximumAge":30,"robotClass":"SUMO"}]""";
    private static final String MEMBERS = """
            [{"fullName":"Ada","birthDate":"2010-05-20","role":"COMPETITOR"},
             {"fullName":"Linus","birthDate":"2011-08-03","role":"COMPETITOR"},
             {"fullName":"Grace","birthDate":"1988-02-10","role":"COACH"}]""";
    private static final String ROBOT = """
            {"name":"Rescuer","robotClass":"RESCUE_BOT","weightKg":2.4,
             "dimensions":{"lengthMm":180,"widthMm":180,"heightMm":150}}""";
    private static final String DOCUMENTS = """
            [{"type":"PARENTAL_CONSENT","reference":"PC-1"},{"type":"TECHNICAL_SHEET","reference":"TS-1"}]""";

    @LocalServerPort int port;

    private record Edition(String seasonId, DateRange period, String competitionId, String juniorId,
            String seniorId) {
    }

    // ---- AC-1: temporada y competencia completas --------------------------------------------------------

    @Test
    void createsASeasonAndACompetitionAndConsultsItWithItsFirstRulebookActive() {
        DateRange period = Editions.nextPeriod();

        ResponseEntity<SeasonCreatedResponse> season = post("/seasons", seasonBody(period),
                SeasonCreatedResponse.class);
        assertEquals(HttpStatus.CREATED, season.getStatusCode());
        String seasonId = season.getBody().seasonId();
        assertTrue(seasonId.startsWith("SEASON-"), seasonId);

        ResponseEntity<CompetitionCreatedResponse> created = post("/seasons/" + seasonId + "/competitions",
                competitionBody("National Open", period, CATEGORIES, rulebook(rescue(""))),
                CompetitionCreatedResponse.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        CompetitionCreatedResponse body = created.getBody();
        assertEquals("/competitions/" + body.competitionId(), created.getHeaders().getLocation().toString());
        assertEquals("v1", body.rulebookVersion());
        assertEquals(List.of("Junior", "Senior"), body.categories().stream()
                .map(CompetitionCreatedResponse.CategoryCreated::name).toList());
        assertNotEquals(body.categories().get(0).categoryId(), body.categories().get(1).categoryId());

        CompetitionResponse found = get("/competitions/" + body.competitionId(), CompetitionResponse.class)
                .getBody();
        assertEquals(body.competitionId(), found.competitionId());
        assertEquals("National Open", found.name());
        assertEquals(period.start().toString(), found.period().start());
        assertEquals(period.end().toString(), found.period().end());
        assertEquals("v1", found.rulebookVersion());
        assertEquals(2, found.categories().size());
        CompetitionResponse.CategoryResponse junior = found.categories().get(0);
        assertEquals(body.categories().get(0).categoryId(), junior.categoryId());
        assertEquals("Junior", junior.name());
        assertEquals(12, junior.minimumAge());
        assertEquals(17, junior.maximumAge());
        assertEquals("RESCUE_BOT", junior.robotClass());
        assertEquals(body.categories().get(1).categoryId(), found.categories().get(1).categoryId());
        assertEquals("SUMO", found.categories().get(1).robotClass());
    }

    @Test
    void auditsTheCreationOfTheCompetitionAndOfEachRulebookVersion() {
        Edition edition = edition(rulebook(rescue("")));
        post("/competitions/" + edition.competitionId() + "/rulebooks", publishBody(rulebook(rescue(""))),
                RulebookPublishedResponse.class);

        AuditTrailResponse trail = get("/audit?subjectId=" + edition.competitionId(), AuditTrailResponse.class)
                .getBody();

        assertEquals(List.of("COMPETITION_CREATED", "RULEBOOK_PUBLISHED", "RULEBOOK_PUBLISHED"),
                trail.events().stream().map(AuditEventResponse::action).toList());
        assertEquals("v1", trail.events().get(1).details().get("version"));
        assertEquals("v2", trail.events().get(2).details().get("version"));
        assertEquals("organizer", trail.events().get(0).actor());
    }

    // ---- AC-2: temporada inexistente --------------------------------------------------------------------

    @Test
    void answersNotFoundForAnUnknownSeasonAndSavesNoCompetition() {
        int before = lastCompetitionNumber();

        ResponseEntity<String> response = post("/seasons/SEASON-UNKNOWN/competitions",
                competitionBody("Ghost", Editions.nextPeriod(), CATEGORIES, rulebook(rescue(""))), String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
        assertEquals(before + 1, lastCompetitionNumber(), "no competition (nor id) was created in between");
    }

    @Test
    void answersNotFoundWhenConsultingOrPublishingOnAnUnknownCompetition() {
        ResponseEntity<String> consulted = get("/competitions/COMPETITION-UNKNOWN", String.class);
        ResponseEntity<String> published = post("/competitions/COMPETITION-UNKNOWN/rulebooks",
                publishBody(rulebook(rescue(""))), String.class);

        for (ResponseEntity<String> response : List.of(consulted, published)) {
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
        }
    }

    // ---- AC-3: configuración inválida -------------------------------------------------------------------

    @Test
    void refusesEveryInvalidRulebookWithAnInputErrorAndCreatesNothing() {
        DateRange period = Editions.nextPeriod();
        String seasonId = post("/seasons", seasonBody(period), SeasonCreatedResponse.class).getBody().seasonId();
        int before = lastCompetitionNumber();
        Map<String, String> invalid = new LinkedHashMap<>();
        invalid.put("unknown scoring rule type", rulebook(withRules(RESCUE_RULES + ",{\"type\":\"MAGIC\"}", "")));
        invalid.put("scoring rule without its parameters", rulebook(withRules(
                RESCUE_RULES.replace(TIME_RULE, "{\"type\":\"TIME\",\"metric\":\"TIME\"}"), "")));
        invalid.put("rule on an undefined metric", rulebook(withRules(
                RESCUE_RULES.replace(TIME_RULE, TIME_RULE.replace("\"metric\":\"TIME\"", "\"metric\":\"NOPE\"")),
                "")));
        invalid.put("fractional bonus points", rulebook(withRules(
                RESCUE_RULES.replace(BONUS_RULE, BONUS_RULE.replace("\"bonusPoints\":15", "\"bonusPoints\":15.5")),
                "")));
        invalid.put("best rounds with N above M", rulebook(rescue("\"bestRounds\":{\"counted\":4,\"outOf\":3}")));
        invalid.put("negative bonus cap", rulebook(rescue("\"bonusCap\":{\"maximumPoints\":-1}")));
        invalid.put("zero attempts", rulebook(rescue("").replace("\"maximumAttempts\":2", "\"maximumAttempts\":0")));
        invalid.put("unknown metric kind", rulebook(rescue("").replace("TIME_SECONDS", "MAGIC")));
        invalid.put("mixed challenge without judge rules", rulebook(
                withRules(String.join(",", TIME_RULE, OBJECTIVES_RULE), "\"mixedSources\":true")));
        invalid.put("duplicated challenge", rulebook(rescue("") + "," + rescue("")));
        invalid.put("no challenges", rulebook(""));
        invalid.put("unknown eligibility rule", rulebook(rescue("")).replace("{\"type\":\"AGE_RANGE\"}",
                "{\"type\":\"HAIR_COLOR\"}"));
        invalid.put("unknown document type", rulebook(rescue("")).replace("PARENTAL_CONSENT", "PASSPORT"));
        invalid.put("robot specification without dimensions", rulebook(rescue("")).replace(
                "\"maximumDimensionsMm\":{\"length\":200,\"width\":200,\"height\":200}", "\"x\":1"));
        invalid.put("unknown attempt aggregation", rulebook(rescue("")).replace(
                "\"attemptAggregation\":{\"type\":\"BEST_ATTEMPT\"}", "\"attemptAggregation\":{\"type\":\"MEDIAN\"}"));
        invalid.put("fastest-metric tiebreak without metric", rulebook(rescue("")).replace(
                ",\"metric\":\"TIME\"}]", "}]"));
        invalid.put("zero appeal window", rulebook(rescue("")).replace("\"appealWindowMinutes\":60",
                "\"appealWindowMinutes\":0"));
        invalid.put("missing eligibility", rulebook(rescue("")).replace("\"eligibility\":" + ELIGIBILITY + ",", ""));

        invalid.forEach((label, rulebook) -> {
            ResponseEntity<String> response = post("/seasons/" + seasonId + "/competitions",
                    competitionBody("Invalid", period, CATEGORIES, rulebook), String.class);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), label + ": " + response.getBody());
            assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), label + ": " + response.getBody());
            assertFalse(response.getBody().contains("Exception"), label + ": " + response.getBody());
        });

        assertEquals(before + 1, lastCompetitionNumber(), "none of the invalid configurations created a competition");
    }

    @Test
    void refusesAnInvalidCompetitionOrSeasonWithTheErrorThatCorrespondsToIt() {
        DateRange period = Editions.nextPeriod();
        String seasonId = post("/seasons", seasonBody(period), SeasonCreatedResponse.class).getBody().seasonId();
        String rulebook = rulebook(rescue(""));
        String outside = "{\"start\":\"2020-01-01\",\"end\":\"2020-01-02\"}";

        assertError(HttpStatus.UNPROCESSABLE_CONTENT, "RULE_VIOLATION", post("/seasons/" + seasonId + "/competitions",
                competitionBody("Outside", outside, CATEGORIES, rulebook), String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons/" + seasonId + "/competitions",
                competitionBody("No categories", period, "[]", rulebook), String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons/" + seasonId + "/competitions",
                competitionBody("Reversed ages", period,
                        "[{\"name\":\"J\",\"minimumAge\":17,\"maximumAge\":12,\"robotClass\":\"X\"}]", rulebook),
                String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons/" + seasonId + "/competitions",
                competitionBody("No robot class", period, "[{\"name\":\"J\",\"minimumAge\":12,\"maximumAge\":17}]",
                        rulebook), String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons/" + seasonId + "/competitions",
                competitionBody("Bad date", "{\"start\":\"junio\",\"end\":\"2026-06-02\"}", CATEGORIES, rulebook),
                String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons/" + seasonId + "/competitions",
                "{\"name\":\"\",\"period\":" + periodJson(period) + ",\"categories\":" + CATEGORIES
                        + ",\"rulebook\":" + rulebook + ",\"actor\":\"organizer\"}", String.class));
        assertError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                post("/seasons/" + seasonId + "/competitions", "{not json", String.class));

        assertError(HttpStatus.CONFLICT, "CONFLICT", post("/seasons", seasonBody(period), String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons", """
                {"name":"Wrong year","year":1999,"period":%s,"actor":"organizer"}""".formatted(periodJson(
                Editions.nextPeriod())), String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons", """
                {"name":"No year","period":%s,"actor":"organizer"}""".formatted(periodJson(Editions.nextPeriod())),
                String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons", """
                {"name":"","year":2026,"period":%s,"actor":"organizer"}""".formatted(periodJson(
                Editions.nextPeriod())), String.class));
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post("/seasons", """
                {"name":"No period","year":2026,"actor":"organizer"}""", String.class));
        assertError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", post("/seasons", "{not json", String.class));
    }

    // ---- AC-4: nueva versión ----------------------------------------------------------------------------

    @Test
    void publishesANewVersionAndUsesItOnlyForRoundsScheduledAfterwards() {
        Edition edition = edition(rulebook(rescue("")));
        String team = registerTeam(edition);
        String first = scheduleRound(edition, "RESCUE", 1, "09:00:00", team);

        ResponseEntity<RulebookPublishedResponse> published = post(
                "/competitions/" + edition.competitionId() + "/rulebooks", publishBody(rulebook(rescue(""))),
                RulebookPublishedResponse.class);

        assertEquals(HttpStatus.CREATED, published.getStatusCode());
        assertEquals("v2", published.getBody().rulebookVersion());
        assertEquals(edition.competitionId(), published.getBody().competitionId());
        assertEquals("v2", get("/competitions/" + edition.competitionId(), CompetitionResponse.class).getBody()
                .rulebookVersion());
        String second = scheduleRound(edition, "RESCUE", 2, "10:00:00", team);
        assertEquals("v1", get("/rounds/" + first, RoundResponse.class).getBody().rulebookVersion());
        assertEquals("v2", get("/rounds/" + second, RoundResponse.class).getBody().rulebookVersion());

        ResponseEntity<RulebookPublishedResponse> third = post(
                "/competitions/" + edition.competitionId() + "/rulebooks", publishBody(rulebook(rescue(""))),
                RulebookPublishedResponse.class);
        assertEquals("v3", third.getBody().rulebookVersion());
    }

    @Test
    void refusesAnInvalidNewRulebookAndKeepsTheActiveVersion() {
        Edition edition = edition(rulebook(rescue("")));
        String invalid = rulebook(rescue("\"bestRounds\":{\"counted\":4,\"outOf\":3}"));

        ResponseEntity<String> response = post("/competitions/" + edition.competitionId() + "/rulebooks",
                publishBody(invalid), String.class);

        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", response);
        assertEquals("v1", get("/competitions/" + edition.competitionId(), CompetitionResponse.class).getBody()
                .rulebookVersion());
        assertError(HttpStatus.BAD_REQUEST, "INVALID_VALUE", post(
                "/competitions/" + edition.competitionId() + "/rulebooks", "{\"actor\":\"organizer\"}",
                String.class));
        assertError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", post(
                "/competitions/" + edition.competitionId() + "/rulebooks", "{not json", String.class));
    }

    // ---- AC-5: F1, F2 y F3 configurables ----------------------------------------------------------------

    @Test
    void acceptsBestRoundsABonusCapAndAMixedChallengeAndTheyTakeEffectWhereTheyAreConsumed() {
        String cappedBonuses = rescue("\"bestRounds\":{\"counted\":2,\"outOf\":3},\"bonusCap\":{\"maximumPoints\":25}");
        Edition edition = edition(rulebook(cappedBonuses + "," + SHOWCASE));
        String team = registerTeam(edition);

        // F1: la cuarta ronda de un desafío que cuenta las 2 mejores de 3 no se puede programar
        scheduleRound(edition, "RESCUE", 1, "08:00:00", team);
        scheduleRound(edition, "RESCUE", 2, "09:00:00", team);
        scheduleRound(edition, "RESCUE", 3, "10:00:00", team);
        ResponseEntity<String> fourth = post("/rounds", roundBody(edition, "RESCUE", 4, "11:00:00", team),
                String.class);
        assertError(HttpStatus.CONFLICT, "CONFLICT", fourth);
        assertTrue(fourth.getBody().contains("best 2 of 3"), fourth.getBody());
        // F3: el desafío mixto declarado en el mismo reglamento queda disponible para programar rondas
        assertNotNull(scheduleRound(edition, "SHOWCASE", 4, "12:00:00", team));
    }

    @Test
    void acceptsEverySupportedVariantOfEveryFamily() {
        String rules = String.join(",", TIME_RULE, OBJECTIVES_RULE, RESOURCE_RULE, BONUS_RULE,
                "{\"type\":\"BONUS\",\"metric\":\"TIME\",\"comparison\":\"AT_MOST\",\"threshold\":60,"
                        + "\"bonusPoints\":10}",
                "{\"type\":\"PRECISION\",\"metric\":\"PRECISION\",\"maximumPoints\":10}", JUDGES_RULE);
        String withPrecisionMetric = withRules(rules, "").replace("{\"key\":\"DESIGN\"",
                "{\"key\":\"PRECISION\",\"kind\":\"PRECISION_RATIO\",\"unit\":\"ratio\","
                        + "\"requirement\":\"REQUIRED\"},{\"key\":\"DESIGN\"");
        String rulebook = rulebook(withPrecisionMetric)
                .replace("\"attemptAggregation\":{\"type\":\"BEST_ATTEMPT\"}",
                        "\"attemptAggregation\":{\"type\":\"SUM_OF_ATTEMPTS\"}")
                .replace("[\"PARENTAL_CONSENT\",\"TECHNICAL_SHEET\"]", "[\"INSURANCE\",\"SAFETY_CHECK\"]");

        ResponseEntity<CompetitionCreatedResponse> response = createCompetition(Editions.nextPeriod(), rulebook);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // ---- helpers de la configuración --------------------------------------------------------------------

    private static String rescue(String options) {
        return withRules(RESCUE_RULES, options);
    }

    private static String withRules(String rules, String options) {
        return challenge("RESCUE", rules, options);
    }

    private static String challenge(String id, String rules, String options) {
        return "{\"id\":\"" + id + "\",\"name\":\"Rescue mission\",\"metrics\":" + METRICS + ",\"scoringRules\":["
                + rules + "],\"penalties\":" + PENALTIES + ",\"maximumAttempts\":2"
                + (options.isEmpty() ? "" : "," + options) + "}";
    }

    private static String rulebook(String challenges) {
        return "{\"challenges\":[" + challenges + "],\"eligibility\":" + ELIGIBILITY
                + ",\"attemptAggregation\":{\"type\":\"BEST_ATTEMPT\"},\"tiebreaks\":" + TIEBREAKS
                + ",\"appealWindowMinutes\":60}";
    }

    private static String periodJson(DateRange period) {
        return "{\"start\":\"" + period.start() + "\",\"end\":\"" + period.end() + "\"}";
    }

    private static String seasonBody(DateRange period) {
        return "{\"name\":\"Season " + period.start() + "\",\"year\":" + period.start().getYear() + ",\"period\":"
                + periodJson(period) + ",\"actor\":\"organizer\"}";
    }

    private static String competitionBody(String name, DateRange period, String categories, String rulebook) {
        return competitionBody(name, periodJson(period), categories, rulebook);
    }

    private static String competitionBody(String name, String period, String categories, String rulebook) {
        return "{\"name\":\"" + name + "\",\"period\":" + period + ",\"categories\":" + categories
                + ",\"rulebook\":" + rulebook + ",\"actor\":\"organizer\"}";
    }

    private static String publishBody(String rulebook) {
        return "{\"rulebook\":" + rulebook + ",\"actor\":\"organizer\"}";
    }

    // ---- preparación ------------------------------------------------------------------------------------

    private Edition edition(String rulebook) {
        DateRange period = Editions.nextPeriod();
        String seasonId = post("/seasons", seasonBody(period), SeasonCreatedResponse.class).getBody().seasonId();
        CompetitionCreatedResponse created = post("/seasons/" + seasonId + "/competitions",
                competitionBody("National Open", period, CATEGORIES, rulebook), CompetitionCreatedResponse.class)
                .getBody();
        return new Edition(seasonId, period, created.competitionId(), created.categories().get(0).categoryId(),
                created.categories().get(1).categoryId());
    }

    private ResponseEntity<CompetitionCreatedResponse> createCompetition(DateRange period, String rulebook) {
        String seasonId = post("/seasons", seasonBody(period), SeasonCreatedResponse.class).getBody().seasonId();
        return post("/seasons/" + seasonId + "/competitions",
                competitionBody("Variants", period, CATEGORIES, rulebook), CompetitionCreatedResponse.class);
    }

    /** Número de la última competencia creada: crear una nueva consume exactamente el siguiente. */
    private int lastCompetitionNumber() {
        DateRange period = Editions.nextPeriod();
        String seasonId = post("/seasons", seasonBody(period), SeasonCreatedResponse.class).getBody().seasonId();
        CompetitionCreatedResponse probe = post("/seasons/" + seasonId + "/competitions",
                competitionBody("Probe", period, CATEGORIES, rulebook(rescue(""))), CompetitionCreatedResponse.class)
                .getBody();
        Matcher matcher = COMPETITION_NUMBER.matcher(probe.competitionId());
        assertTrue(matcher.matches(), probe.competitionId());
        return Integer.parseInt(matcher.group(1));
    }

    private String registerTeam(Edition edition) {
        ResponseEntity<String> response = post("/teams", "{\"competitionId\":\"" + edition.competitionId()
                + "\",\"categoryId\":\"" + edition.juniorId() + "\",\"teamName\":\"Rescue Crew\",\"members\":"
                + MEMBERS + ",\"robot\":" + ROBOT + ",\"documents\":" + DOCUMENTS + ",\"actor\":\"organizer\"}",
                String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
        Matcher matcher = Pattern.compile("\"teamId\":\"([^\"]+)\"").matcher(response.getBody());
        assertTrue(matcher.find(), response.getBody());
        return matcher.group(1);
    }

    private String scheduleRound(Edition edition, String challenge, int ordinal, String time, String team) {
        ResponseEntity<String> response = post("/rounds", roundBody(edition, challenge, ordinal, time, team),
                String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
        Matcher matcher = Pattern.compile("\"roundId\":\"([^\"]+)\"").matcher(response.getBody());
        assertTrue(matcher.find(), response.getBody());
        return matcher.group(1);
    }

    private static String roundBody(Edition edition, String challenge, int ordinal, String time, String team) {
        LocalDate day = edition.period().start();
        return "{\"competitionId\":\"" + edition.competitionId() + "\",\"categoryId\":\"" + edition.juniorId()
                + "\",\"challengeId\":\"" + challenge + "\",\"ordinal\":" + ordinal + ",\"heats\":[{\"teamId\":\""
                + team + "\",\"arenaId\":\"A1\",\"start\":\"" + day + "T" + time
                + "\",\"durationMinutes\":15,\"judges\":[\"J1\"]}],\"actor\":\"organizer\"}";
    }

    private static void assertError(HttpStatus status, String code, ResponseEntity<String> response) {
        assertEquals(status, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"code\":\"" + code + "\""), response.getBody());
    }

    // ---- HTTP -------------------------------------------------------------------------------------------

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
