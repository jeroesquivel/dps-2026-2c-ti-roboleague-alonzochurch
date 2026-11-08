package com.dps.roboleague.adapter.in.graphql;

import com.dps.roboleague.adapter.in.web.standings.StandingsResponse;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.TeamId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La tabla de posiciones por GraphQL, comparada con la que devuelve la API REST para la misma categoría. Cubre AC-4
 * de {@code SPEC_api-graphql.md} y la consulta de una tabla que no existe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Import(GraphQlEditions.class)
class StandingsGraphQlTest {

    private static final String STANDINGS = """
            query ($competitionId: ID!, $categoryId: ID!) {
              standings(competitionId: $competitionId, categoryId: $categoryId) {
                revision status rulebookVersion generatedAt
                entries {
                  position totalPoints team { name robot { name } }
                  appliedTiebreaks { code description }
                  explanation { subtotals { code description points rounds { ordinal points status reason } } }
                }
              }
            }""";

    @Autowired HttpGraphQlTester graphQl;
    @Autowired GraphQlEditions editions;
    @LocalServerPort int port;

    // ---- AC-4: posiciones con nombre del equipo --------------------------------------------------------

    @Test
    void answersTheStandingsWithTheTeamOfEachRowInOneRequestWithTheSameValuesAsRest() {
        GraphQlEditions.Edition edition = generatedEdition("Standings Cup");
        StandingsResponse rest = rest(edition);

        GraphQlTester.Response response = query(edition);

        response.path("standings.revision").entity(Integer.class).isEqualTo(rest.revision())
                .path("standings.status").entity(String.class).isEqualTo("PROVISIONAL")
                .path("standings.rulebookVersion").entity(String.class).isEqualTo(rest.rulebookVersion())
                .path("standings.generatedAt").entity(String.class).isEqualTo(rest.generatedAt())
                .path("standings.entries[*].position").entityList(Integer.class)
                .containsExactly(rest.entries().stream().map(StandingsResponse.EntryResponse::position)
                        .toArray(Integer[]::new))
                .path("standings.entries[*].team.name").entityList(String.class)
                .containsExactly("Alpha Bots", "Omega Crew")
                .path("standings.entries[*].team.name").entityList(String.class)
                .containsExactly(rest.entries().stream().map(StandingsResponse.EntryResponse::teamName)
                        .toArray(String[]::new));
        response.path("standings.entries[*].totalPoints").entityList(BigDecimal.class)
                .satisfies(points -> assertSamePoints(rest.entries().stream()
                        .map(StandingsResponse.EntryResponse::totalPoints).toList(), points));
    }

    @Test
    void explainsEachTotalWithTheSameSubtotalsAsRest() {
        GraphQlEditions.Edition edition = generatedEdition("Explained Cup");
        StandingsResponse.EntryResponse leader = rest(edition).entries().getFirst();

        GraphQlTester.Response response = query(edition);

        response.path("standings.entries[0].explanation.subtotals[*].code").entityList(String.class)
                .containsExactly(leader.explanation().subtotals().stream()
                        .map(StandingsResponse.SubtotalResponse::code).toArray(String[]::new))
                .path("standings.entries[0].appliedTiebreaks").entityList(Object.class)
                .hasSize(leader.appliedTiebreaks().size());
        response.path("standings.entries[0].explanation.subtotals[*].points").entityList(BigDecimal.class)
                .satisfies(points -> assertSamePoints(leader.explanation().subtotals().stream()
                        .map(StandingsResponse.SubtotalResponse::points).toList(), points));
    }

    @Test
    void reportsACategoryWithoutStandingsAsNotFound() {
        GraphQlEditions.Edition edition = editions.create("Empty Cup");

        graphQl.document(STANDINGS)
                .variable("competitionId", edition.competitionId().value())
                .variable("categoryId", edition.junior().value())
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && "NOT_FOUND".equals(error.getExtensions().get("code"))
                        && "standings".equals(error.getPath()))
                .verify()
                .path("standings").valueIsNull();
    }

    // ---- datos ------------------------------------------------------------------------------------------

    /** Alpha cumple 5 objetivos y Omega 4: Alpha queda primera. */
    private GraphQlEditions.Edition generatedEdition(String name) {
        GraphQlEditions.Edition edition = editions.create(name);
        TeamId omega = editions.registerEligible(edition, "Omega Crew");
        TeamId alpha = editions.registerEligible(edition, "Alpha Bots");
        RoundId round = editions.scheduleRound(edition, 1, List.of(alpha, omega));
        editions.capture(round, alpha, 1, 5);
        editions.capture(round, omega, 1, 4);
        editions.generateStandings(edition);
        return edition;
    }

    private GraphQlTester.Response query(GraphQlEditions.Edition edition) {
        return graphQl.document(STANDINGS)
                .variable("competitionId", edition.competitionId().value())
                .variable("categoryId", edition.junior().value())
                .execute();
    }

    private StandingsResponse rest(GraphQlEditions.Edition edition) {
        return RestClient.create("http://localhost:" + port).get()
                .uri("/competitions/{competition}/categories/{category}/standings",
                        edition.competitionId().value(), edition.junior().value())
                .retrieve().body(StandingsResponse.class);
    }

    private static void assertSamePoints(List<BigDecimal> expected, List<BigDecimal> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(0, expected.get(index).compareTo(actual.get(index)),
                    "expected " + expected.get(index) + " but got " + actual.get(index));
        }
    }
}
