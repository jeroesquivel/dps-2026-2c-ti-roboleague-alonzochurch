package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamApiTest {

    private static final String ELIGIBLE_MEMBERS = """
            [{"fullName":"Ada","birthDate":"2010-05-20","role":"COMPETITOR"},
             {"fullName":"Linus","birthDate":"2011-08-03","role":"COMPETITOR"},
             {"fullName":"Grace","birthDate":"1988-02-10","role":"COACH"}]""";
    private static final String ELIGIBLE_ROBOT = robot("Rescuer", "RESCUE_BOT", "2.4");
    private static final String COMPLETE_DOCUMENTS = """
            [{"type":"PARENTAL_CONSENT","reference":"PC-1"},{"type":"TECHNICAL_SHEET","reference":"TS-1"}]""";
    private static final Actor ACTOR = Actor.of("test-actor");
    private static final Pattern TEAM_ID = Pattern.compile("\"teamId\":\"([^\"]+)\"");

    @Autowired CreateSeason createSeason;
    @Autowired CreateCompetition createCompetition;
    @LocalServerPort int port;

    private String competitionId;
    private String categoryId;

    @BeforeEach
    void createEdition() {
        CreateCompetition.Result competition = newCompetition("National Open");
        competitionId = competition.competitionId().value();
        categoryId = competition.firstCategory().value();
    }

    @Test
    void registersAnEligibleTeamAndFindsItAfterwards() {
        ResponseEntity<String> created = post("/teams",
                body(competitionId, categoryId, "Rescue Crew", ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS));

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        String teamId = teamIdOf(created);
        assertEquals("/teams/" + teamId, created.getHeaders().getLocation().toString());
        assertTrue(created.getBody().contains("\"status\":\"ACCEPTED\""), created.getBody());
        assertTrue(created.getBody().contains("\"violations\":[]"), created.getBody());

        ResponseEntity<String> found = get("/teams/" + teamId);
        assertEquals(HttpStatus.OK, found.getStatusCode());
        String json = found.getBody();
        assertTrue(json.contains("\"status\":\"ACCEPTED\""), json);
        assertTrue(json.contains("\"competitionId\":\"" + competitionId + "\""), json);
        assertTrue(json.contains("\"categoryId\":\"" + categoryId + "\""), json);
        assertTrue(json.contains("\"name\":\"Rescue Crew\""), json);
        assertTrue(json.contains("\"fullName\":\"Grace\""), json);
        assertTrue(json.contains("\"birthDate\":\"2010-05-20\""), json);
        assertTrue(json.contains("\"robotClass\":\"RESCUE_BOT\""), json);
        assertTrue(json.contains("\"documentTypes\":[\"PARENTAL_CONSENT\",\"TECHNICAL_SHEET\"]"), json);
        assertTrue(json.contains("\"violations\":[]"), json);
    }

    @Test
    void rejectsAnIneligibleTeamListingEveryViolationAndKeepsThemForLaterQueries() {
        String members = """
                [{"fullName":"Ada","birthDate":"2010-05-20","role":"COMPETITOR"},
                 {"fullName":"Tim","birthDate":"2018-01-15","role":"COMPETITOR"}]""";
        String documents = "[{\"type\":\"PARENTAL_CONSENT\",\"reference\":\"PC-1\"}]";

        ResponseEntity<String> created = post("/teams", body(competitionId, categoryId, "Heavy Sumo", members,
                robot("Sumo", "SUMO", "4.1"), documents));

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertViolations(created.getBody());
        assertTrue(created.getBody().contains("\"status\":\"REJECTED\""), created.getBody());

        ResponseEntity<String> found = get("/teams/" + teamIdOf(created));
        assertEquals(HttpStatus.OK, found.getStatusCode());
        assertTrue(found.getBody().contains("\"status\":\"REJECTED\""), found.getBody());
        assertViolations(found.getBody());
    }

    @Test
    void reportsAnUnknownCompetitionAsNotFound() {
        ResponseEntity<String> response = post("/teams",
                body("COMP-UNKNOWN", categoryId, "Ghosts", ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
    }

    @Test
    void reportsAnUnknownCategoryAsNotFound() {
        ResponseEntity<String> response = post("/teams",
                body(competitionId, "CAT-UNKNOWN", "Ghosts", ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
    }

    @Test
    void reportsACategoryOfAnotherCompetitionAsNotFound() {
        String foreignCategory = newCompetition("Regional Cup").firstCategory().value();

        ResponseEntity<String> response = post("/teams",
                body(competitionId, foreignCategory, "Lost", ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("does not belong to competition"), response.getBody());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedInputs")
    void rejectsInputsThatBreakAnInvariantOrAreMalformed(String description, String name, String members,
            String robot, String documents, String actor) {
        String json = """
                {"competitionId":"%s","categoryId":"%s","teamName":%s,"members":%s,"robot":%s,"documents":%s,
                 "actor":%s}""".formatted(competitionId, categoryId, name, members, robot, documents, actor);

        ResponseEntity<String> response = post("/teams", json);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
    }

    static Stream<Arguments> malformedInputs() {
        String name = "\"Crew\"";
        String actor = "\"organizer\"";
        return Stream.of(
                Arguments.of("blank team name", "\"  \"", ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS, actor),
                Arguments.of("missing team name", "null", ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS,
                        actor),
                Arguments.of("no members", name, "[]", ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS, actor),
                Arguments.of("missing members", name, "null", ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS, actor),
                Arguments.of("null member", name, "[null]", ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS, actor),
                Arguments.of("blank member name", name,
                        "[{\"fullName\":\"\",\"birthDate\":\"2010-05-20\",\"role\":\"COMPETITOR\"}]",
                        ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS, actor),
                Arguments.of("missing birth date", name,
                        "[{\"fullName\":\"Ada\",\"role\":\"COMPETITOR\"}]", ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS,
                        actor),
                Arguments.of("birth date that is not ISO", name,
                        "[{\"fullName\":\"Ada\",\"birthDate\":\"20/05/2010\",\"role\":\"COMPETITOR\"}]",
                        ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS, actor),
                Arguments.of("unknown member role", name,
                        "[{\"fullName\":\"Ada\",\"birthDate\":\"2010-05-20\",\"role\":\"CAPTAIN\"}]",
                        ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS, actor),
                Arguments.of("missing member role", name,
                        "[{\"fullName\":\"Ada\",\"birthDate\":\"2010-05-20\"}]", ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS,
                        actor),
                Arguments.of("missing robot", name, ELIGIBLE_MEMBERS, "null", COMPLETE_DOCUMENTS, actor),
                Arguments.of("robot without name", name, ELIGIBLE_MEMBERS, robot("", "RESCUE_BOT", "2.4"),
                        COMPLETE_DOCUMENTS, actor),
                Arguments.of("robot without class", name, ELIGIBLE_MEMBERS,
                        "{\"name\":\"R\",\"weightKg\":2.4,\"dimensions\":{\"lengthMm\":1,\"widthMm\":1,\"heightMm\":1}}",
                        COMPLETE_DOCUMENTS, actor),
                Arguments.of("robot with a non positive weight", name, ELIGIBLE_MEMBERS,
                        robot("Rescuer", "RESCUE_BOT", "0"), COMPLETE_DOCUMENTS, actor),
                Arguments.of("robot without weight", name, ELIGIBLE_MEMBERS,
                        "{\"name\":\"R\",\"robotClass\":\"RESCUE_BOT\",\"dimensions\":"
                                + "{\"lengthMm\":1,\"widthMm\":1,\"heightMm\":1}}",
                        COMPLETE_DOCUMENTS, actor),
                Arguments.of("robot without dimensions", name, ELIGIBLE_MEMBERS,
                        "{\"name\":\"R\",\"robotClass\":\"RESCUE_BOT\",\"weightKg\":2.4}", COMPLETE_DOCUMENTS, actor),
                Arguments.of("robot with a missing dimension", name, ELIGIBLE_MEMBERS,
                        "{\"name\":\"R\",\"robotClass\":\"RESCUE_BOT\",\"weightKg\":2.4,"
                                + "\"dimensions\":{\"lengthMm\":1,\"widthMm\":1}}",
                        COMPLETE_DOCUMENTS, actor),
                Arguments.of("unknown document type", name, ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT,
                        "[{\"type\":\"PASSPORT\",\"reference\":\"P-1\"}]", actor),
                Arguments.of("document without type", name, ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT,
                        "[{\"reference\":\"P-1\"}]", actor),
                Arguments.of("document without reference", name, ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT,
                        "[{\"type\":\"INSURANCE\",\"reference\":\" \"}]", actor),
                Arguments.of("null document", name, ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, "[null]", actor),
                Arguments.of("missing actor", name, ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS, "null"));
    }

    @Test
    void rejectsABodyThatIsNotValidJson() {
        ResponseEntity<String> response = post("/teams", "{not json");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"MALFORMED_REQUEST\""), response.getBody());
    }

    @Test
    void rejectsAMissingIdentifierAsInvalidInput() {
        String json = body(null, categoryId, "Crew", ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT, COMPLETE_DOCUMENTS);

        ResponseEntity<String> response = post("/teams", json);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
    }

    @Test
    void treatsAnAbsentDocumentListAsNoDocumentsAndReportsTheMissingOnes() {
        String json = """
                {"competitionId":"%s","categoryId":"%s","teamName":"Paperless","members":%s,"robot":%s,
                 "actor":"organizer"}""".formatted(competitionId, categoryId, ELIGIBLE_MEMBERS, ELIGIBLE_ROBOT);

        ResponseEntity<String> response = post("/teams", json);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"REJECTED\""), response.getBody());
        assertTrue(response.getBody().contains("\"code\":\"REQUIRED_DOCUMENTS\""), response.getBody());
    }

    @Test
    void reportsAnUnknownTeamAsNotFound() {
        ResponseEntity<String> response = get("/teams/TEAM-UNKNOWN");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
    }

    private CreateCompetition.Result newCompetition(String name) {
        DateRange period = Editions.nextPeriod();
        SeasonId seasonId = createSeason.execute(
                new CreateSeason.Command("Season " + period.start(), period.start().getYear(), period, ACTOR));
        return createCompetition.execute(new CreateCompetition.Command(seasonId, name, period,
                List.of(new CreateCompetition.CategoryDraft("Junior", AgeRange.between(12, 17),
                        TeamFixtures.RESCUE_BOT)),
                RescueEditionFixture.rulebook(), ACTOR));
    }

    private static void assertViolations(String json) {
        for (String code : List.of("AGE_RANGE", "TEAM_COMPOSITION", "ROBOT_CLASS", "ROBOT_SPECIFICATION",
                "REQUIRED_DOCUMENTS")) {
            assertTrue(json.contains("\"code\":\"" + code + "\""), code + " missing in " + json);
        }
        assertTrue(json.contains("\"reason\":\""), json);
    }

    private static String teamIdOf(ResponseEntity<String> response) {
        Matcher matcher = TEAM_ID.matcher(response.getBody());
        assertTrue(matcher.find(), response.getBody());
        String teamId = matcher.group(1);
        assertNotNull(teamId);
        return teamId;
    }

    private static String robot(String name, String robotClass, String weightKg) {
        return "{\"name\":\"%s\",\"robotClass\":\"%s\",\"weightKg\":%s,\"dimensions\":{\"lengthMm\":180,"
                .formatted(name, robotClass, weightKg) + "\"widthMm\":180,\"heightMm\":150}}";
    }

    private static String body(String competition, String category, String name, String members, String robot,
            String documents) {
        String competitionJson = competition == null ? "null" : "\"" + competition + "\"";
        return """
                {"competitionId":%s,"categoryId":"%s","teamName":"%s","members":%s,"robot":%s,"documents":%s,
                 "actor":"organizer"}""".formatted(competitionJson, category, name, members, robot, documents);
    }

    private ResponseEntity<String> post(String path, String json) {
        return client().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json)
                .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(String.class);
    }

    private ResponseEntity<String> get(String path) {
        return client().get().uri(path).retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(String.class);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}
