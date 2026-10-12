package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.team.Robot;
import com.dps.roboleague.domain.team.TeamMembers;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.time.LocalDate;
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
class RoundApiTest {

    private static final Actor ACTOR = Actor.of("test-actor");
    private static final String CHALLENGE = "RESCUE";
    private static final Pattern ROUND_ID = Pattern.compile("\"roundId\":\"([^\"]+)\"");

    @Autowired CreateSeason createSeason;
    @Autowired CreateCompetition createCompetition;
    @Autowired RegisterTeam registerTeam;
    @LocalServerPort int port;

    private Edition edition;
    private String alpha;
    private String bravo;
    private String charlie;

    /** Competencia con su categoría y el primer día del evento. */
    private record Edition(String competitionId, String categoryId, LocalDate day) {

        String at(String time) {
            return day + "T" + time;
        }
    }

    @BeforeEach
    void createEditionWithThreeAcceptedTeams() {
        edition = newEdition(RescueEditionFixture.rulebook());
        alpha = register(edition, "Alpha", TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot());
        bravo = register(edition, "Bravo", TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot());
        charlie = register(edition, "Charlie", TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot());
    }

    @Test
    void schedulesARoundAndFindsItWithTheFixedRulebookVersion() {
        String json = round(edition, 1, heat(alpha, "A1", edition.at("10:00:00"), 15, "J1"),
                heat(bravo, "A1", edition.at("10:30"), 20, "J1", "J2"));

        ResponseEntity<String> created = post(json);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        String roundId = roundIdOf(created);
        assertEquals("/rounds/" + roundId, created.getHeaders().getLocation().toString());

        ResponseEntity<String> found = get("/rounds/" + roundId);
        assertEquals(HttpStatus.OK, found.getStatusCode());
        String body = found.getBody();
        assertTrue(body.contains("\"competitionId\":\"" + edition.competitionId() + "\""), body);
        assertTrue(body.contains("\"categoryId\":\"" + edition.categoryId() + "\""), body);
        assertTrue(body.contains("\"challengeId\":\"RESCUE\""), body);
        assertTrue(body.contains("\"ordinal\":1"), body);
        assertTrue(body.contains("\"rulebookVersion\":\"v1\""), body);
        assertTrue(body.contains("\"teamId\":\"" + alpha + "\""), body);
        assertTrue(body.contains("\"teamId\":\"" + bravo + "\""), body);
        assertTrue(body.contains("\"start\":\"" + edition.at("10:00:00") + "\""), body);
        assertTrue(body.contains("\"end\":\"" + edition.at("10:15:00") + "\""), body);
        assertTrue(body.contains("\"start\":\"" + edition.at("10:30:00") + "\""), body);
        assertTrue(body.contains("\"durationMinutes\":20"), body);
        assertTrue(body.contains("\"judges\":[\"J1\",\"J2\"]"), body);
    }

    @Test
    void rejectsATeamThatDoesNotExistAndDoesNotKeepTheRound() {
        ResponseEntity<String> response = post(round(edition, 1,
                heat(alpha, "A1", edition.at("10:00:00"), 15, "J1"),
                heat("TEAM-UNKNOWN", "A2", edition.at("11:00:00"), 15, "J2")));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
        assertOrdinalOneIsStillFree();
    }

    @Test
    void rejectsATeamWhoseRegistrationWasRejected() {
        String rejected = register(edition, "Heavy", TeamFixtures.eligibleMembers(), TeamFixtures.overweightRobot());

        ResponseEntity<String> response = post(round(edition, 1,
                heat(rejected, "A1", edition.at("10:00:00"), 15, "J1")));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"RULE_VIOLATION\""), response.getBody());
        assertOrdinalOneIsStillFree();
    }

    @Test
    void rejectsATeamRegisteredInAnotherCompetition() {
        Edition other = newEdition(RescueEditionFixture.rulebook());
        String foreign = register(other, "Foreign", TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot());

        ResponseEntity<String> response = post(round(edition, 1,
                heat(foreign, "A1", edition.at("10:00:00"), 15, "J1")));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"RULE_VIOLATION\""), response.getBody());
        assertOrdinalOneIsStillFree();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resourceConflicts")
    void reportsTheTypeOfAResourceConflictAndDoesNotKeepAPartialRound(String conflictType, String firstTeam,
            String firstArena, String firstJudge, String secondTeam, String secondArena, String secondJudge) {
        ResponseEntity<String> response = post(round(edition, 1,
                heat(team(firstTeam), firstArena, edition.at("10:00:00"), 30, firstJudge),
                heat(team(secondTeam), secondArena, edition.at("10:15:00"), 30, secondJudge)));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body.contains("\"code\":\"SCHEDULE_CONFLICT\""), body);
        assertTrue(body.contains("\"type\":\"" + conflictType + "\""), body);
        assertTrue(body.contains("\"detail\":\""), body);
        assertOrdinalOneIsStillFree();
    }

    static Stream<Arguments> resourceConflicts() {
        return Stream.of(
                Arguments.of("TEAM_BUSY", "alpha", "A1", "J1", "alpha", "A2", "J2"),
                Arguments.of("ARENA_BUSY", "alpha", "A1", "J1", "bravo", "A1", "J2"),
                Arguments.of("JUDGE_BUSY", "alpha", "A1", "J1", "bravo", "A2", "J1"));
    }

    @Test
    void reportsAConflictWithAHeatOfAnotherRound() {
        assertEquals(HttpStatus.CREATED, post(round(edition, 1,
                heat(alpha, "A1", edition.at("10:00:00"), 30, "J1"))).getStatusCode());

        ResponseEntity<String> response = post(round(edition, 2,
                heat(bravo, "A1", edition.at("10:10:00"), 15, "J2")));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("\"type\":\"ARENA_BUSY\""), response.getBody());
    }

    @Test
    void allowsHeatsThatStartExactlyWhenAnotherEnds() {
        ResponseEntity<String> response = post(round(edition, 1,
                heat(alpha, "A1", edition.at("10:00:00"), 15, "J1"),
                heat(bravo, "A1", edition.at("10:15:00"), 15, "J1")));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void rejectsARepeatedOrdinalInTheSameCategory() {
        assertEquals(HttpStatus.CREATED, post(round(edition, 1,
                heat(alpha, "A1", edition.at("10:00:00"), 15, "J1"))).getStatusCode());

        ResponseEntity<String> response = post(round(edition, 1,
                heat(bravo, "A2", edition.at("12:00:00"), 15, "J2")));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"CONFLICT\""), response.getBody());
    }

    @Test
    void rejectsAnotherRoundWhenTheChallengeCountsTheBestRoundsAndAlreadyHasM() {
        Edition limited = newEdition(RescueEditionFixture.rulebook(
                RescueEditionFixture.rescueChallengeCountingBestRounds(BestRounds.of(1, 2))));
        String team = register(limited, "Limited", TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot());

        assertEquals(HttpStatus.CREATED,
                post(round(limited, 1, heat(team, "A1", limited.at("09:00:00"), 15, "J1"))).getStatusCode());
        assertEquals(HttpStatus.CREATED,
                post(round(limited, 2, heat(team, "A1", limited.at("10:00:00"), 15, "J1"))).getStatusCode());

        ResponseEntity<String> response = post(round(limited, 3,
                heat(team, "A1", limited.at("11:00:00"), 15, "J1")));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"CONFLICT\""), response.getBody());
        assertTrue(response.getBody().contains("already has 2 rounds"), response.getBody());
    }

    @Test
    void rejectsAHeatOutsideTheCompetitionPeriod() {
        ResponseEntity<String> response = post(round(edition, 1,
                heat(alpha, "A1", edition.day().plusDays(10) + "T10:00:00", 15, "J1")));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"RULE_VIOLATION\""), response.getBody());
        assertOrdinalOneIsStillFree();
    }

    @Test
    void reportsUnknownReferencesAsNotFound() {
        String heats = heat(alpha, "A1", edition.at("10:00:00"), 15, "J1");
        List<String> bodies = List.of(
                body("COMP-UNKNOWN", edition.categoryId(), CHALLENGE, "1", heats),
                body(edition.competitionId(), "CAT-UNKNOWN", CHALLENGE, "1", heats),
                body(edition.competitionId(), edition.categoryId(), "NO-SUCH-CHALLENGE", "1", heats));

        for (String json : bodies) {
            ResponseEntity<String> response = post(json);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(), response.getBody());
            assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
        }
    }

    @Test
    void reportsAnUnknownRoundAsNotFound() {
        ResponseEntity<String> response = get("/rounds/ROUND-UNKNOWN");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"NOT_FOUND\""), response.getBody());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedInputs")
    void rejectsInputsThatBreakAnInvariantOrAreMalformed(String description, String ordinal, String heats,
            String actor) {
        String json = """
                {"competitionId":"%s","categoryId":"%s","challengeId":"RESCUE","ordinal":%s,"heats":%s,
                 "actor":%s}""".formatted(edition.competitionId(), edition.categoryId(), ordinal, heats, actor);

        ResponseEntity<String> response = post(json);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), response.getBody());
        assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
        assertOrdinalOneIsStillFree();
    }

    static Stream<Arguments> malformedInputs() {
        String actor = "\"organizer\"";
        String ok = "{\"teamId\":\"TEAM-1\",\"arenaId\":\"A1\",\"start\":\"2026-03-02T10:00:00\","
                + "\"durationMinutes\":15,\"judges\":[\"J1\"]}";
        return Stream.of(
                Arguments.of("no heats", "1", "[]", actor),
                Arguments.of("missing heats", "1", "null", actor),
                Arguments.of("null heat", "1", "[null]", actor),
                Arguments.of("ordinal zero", "0", "[" + ok + "]", actor),
                Arguments.of("missing ordinal", "null", "[" + ok + "]", actor),
                Arguments.of("missing actor", "1", "[" + ok + "]", "null"),
                Arguments.of("start with Z", "1", "[" + ok.replace("10:00:00", "10:00:00Z") + "]", actor),
                Arguments.of("start with offset", "1", "[" + ok.replace("10:00:00", "10:00:00-03:00") + "]", actor),
                Arguments.of("start that is not a date", "1", "[" + ok.replace("2026-03-02T10:00:00", "tomorrow") + "]",
                        actor),
                Arguments.of("missing start", "1", "[" + ok.replace("\"start\":\"2026-03-02T10:00:00\",", "") + "]",
                        actor),
                Arguments.of("zero duration", "1", "[" + ok.replace("15", "0") + "]", actor),
                Arguments.of("negative duration", "1", "[" + ok.replace("15", "-5") + "]", actor),
                Arguments.of("missing duration", "1", "[" + ok.replace("\"durationMinutes\":15,", "") + "]", actor),
                Arguments.of("no judges", "1", "[" + ok.replace("[\"J1\"]", "[]") + "]", actor),
                Arguments.of("missing judges", "1", "[" + ok.replace("\"judges\":[\"J1\"]", "\"judges\":null") + "]",
                        actor),
                Arguments.of("null judge", "1", "[" + ok.replace("[\"J1\"]", "[null]") + "]", actor),
                Arguments.of("blank judge", "1", "[" + ok.replace("[\"J1\"]", "[\" \"]") + "]", actor),
                Arguments.of("blank arena", "1", "[" + ok.replace("\"A1\"", "\"\"") + "]", actor),
                Arguments.of("blank team", "1", "[" + ok.replace("\"TEAM-1\"", "\" \"") + "]", actor));
    }

    @Test
    void rejectsTheSameTeamTwiceInOneRoundEvenWhenTheHeatsDoNotOverlap() {
        ResponseEntity<String> response = post(round(edition, 1,
                heat(alpha, "A1", edition.at("10:00:00"), 15, "J1"),
                heat(alpha, "A1", edition.at("12:00:00"), 15, "J1")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"INVALID_VALUE\""), response.getBody());
        assertOrdinalOneIsStillFree();
    }

    @Test
    void rejectsABodyThatIsNotValidJson() {
        ResponseEntity<String> response = post("{not json");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("\"code\":\"MALFORMED_REQUEST\""), response.getBody());
    }

    private void assertOrdinalOneIsStillFree() {
        ResponseEntity<String> response = post(round(edition, 1,
                heat(charlie, "Z9", edition.at("16:00:00"), 15, "J9")));

        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "the failed attempt left a round behind: "
                + response.getBody());
    }

    private String team(String name) {
        return switch (name) {
            case "alpha" -> alpha;
            case "bravo" -> bravo;
            default -> charlie;
        };
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
        return new Edition(competition.competitionId().value(), competition.firstCategory().value(), period.start());
    }

    private String register(Edition target, String name, TeamMembers members, Robot robot) {
        List<RegisterTeam.MemberDraft> drafts = members.members().stream()
                .map(member -> new RegisterTeam.MemberDraft(member.fullName(), member.birthDate(), member.role()))
                .toList();
        return registerTeam.execute(new RegisterTeam.Command(
                com.dps.roboleague.domain.shared.CompetitionId.of(target.competitionId()),
                com.dps.roboleague.domain.shared.CategoryId.of(target.categoryId()), name, drafts, robot,
                TeamFixtures.completeDocuments(), ACTOR)).teamId().value();
    }

    private static String heat(String team, String arena, String start, int minutes, String... judges) {
        String judgeList = Stream.of(judges).map(judge -> "\"" + judge + "\"").collect(java.util.stream.Collectors.joining(","));
        return "{\"teamId\":\"%s\",\"arenaId\":\"%s\",\"start\":\"%s\",\"durationMinutes\":%d,\"judges\":[%s]}"
                .formatted(team, arena, start, minutes, judgeList);
    }

    private static String round(Edition target, int ordinal, String... heats) {
        return body(target.competitionId(), target.categoryId(), CHALLENGE, String.valueOf(ordinal),
                String.join(",", heats));
    }

    private static String body(String competition, String category, String challenge, String ordinal, String heats) {
        return "{\"competitionId\":\"%s\",\"categoryId\":\"%s\",\"challengeId\":\"%s\",\"ordinal\":%s,\"heats\":[%s],"
                .formatted(competition, category, challenge, ordinal, heats) + "\"actor\":\"organizer\"}";
    }

    private static String roundIdOf(ResponseEntity<String> response) {
        Matcher matcher = ROUND_ID.matcher(response.getBody());
        assertTrue(matcher.find(), response.getBody());
        return matcher.group(1);
    }

    private ResponseEntity<String> post(String json) {
        return client().post().uri("/rounds").contentType(MediaType.APPLICATION_JSON).body(json)
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
