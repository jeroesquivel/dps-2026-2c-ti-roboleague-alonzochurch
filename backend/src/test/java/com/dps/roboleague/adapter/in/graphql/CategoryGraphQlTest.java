package com.dps.roboleague.adapter.in.graphql;

import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.TeamId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

/**
 * Los campos de una categoría que se cargan aparte: equipos, rondas con sus turnos y tabla de posiciones. Cubre
 * AC-2 y AC-11 de {@code SPEC_api-graphql.md}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Import(GraphQlEditions.class)
class CategoryGraphQlTest {

    @Autowired HttpGraphQlTester graphQl;
    @Autowired GraphQlEditions editions;

    // ---- AC-2: equipos y rondas de una categoría --------------------------------------------------------

    @Test
    void listsEveryTeamOfTheCategoryWithItsStatusOrderedByName() {
        GraphQlEditions.Edition edition = editions.create("Teams Cup");
        editions.registerEligible(edition, "Omega Crew");
        editions.registerEligible(edition, "Alpha Bots");
        editions.registerRejected(edition, "Delta Bots");

        graphQl.document("""
                        query ($id: ID!) {
                          competition(id: $id) { categories { name teams { name status robot { robotClass } } } }
                        }""")
                .variable("id", edition.competitionId().value())
                .execute()
                .path("competition.categories[0].teams[*].name").entityList(String.class)
                .containsExactly("Alpha Bots", "Delta Bots", "Omega Crew")
                .path("competition.categories[0].teams[*].status").entityList(String.class)
                .containsExactly("ACCEPTED", "REJECTED", "ACCEPTED")
                .path("competition.categories[0].teams[0].robot.robotClass").entity(String.class)
                .isEqualTo("RESCUE_BOT")
                .path("competition.categories[1].teams").entityList(Object.class).hasSize(0);
    }

    @Test
    void listsTheRoundsOfTheCategoryInOrderWithTheTeamOfEachHeat() {
        GraphQlEditions.Edition edition = editions.create("Rounds Cup");
        TeamId alpha = editions.registerEligible(edition, "Alpha Bots");
        TeamId omega = editions.registerEligible(edition, "Omega Crew");
        editions.scheduleRound(edition, 2, List.of(omega));
        RoundId first = editions.scheduleRound(edition, 1, List.of(alpha, omega));
        String day = edition.period().start().toString();

        graphQl.document("""
                        query ($id: ID!) {
                          competition(id: $id) {
                            categories {
                              rounds { id ordinal challengeId rulebookVersion
                                       heats { team { name } arena start end judges } }
                            }
                          }
                        }""")
                .variable("id", edition.competitionId().value())
                .execute()
                .path("competition.categories[0].rounds[*].ordinal").entityList(Integer.class).containsExactly(1, 2)
                .path("competition.categories[0].rounds[0].id").entity(String.class).isEqualTo(first.value())
                .path("competition.categories[0].rounds[0].challengeId").entity(String.class).isEqualTo("RESCUE")
                .path("competition.categories[0].rounds[0].rulebookVersion").entity(String.class).isEqualTo("v1")
                .path("competition.categories[0].rounds[0].heats[*].team.name").entityList(String.class)
                .containsExactly("Alpha Bots", "Omega Crew")
                .path("competition.categories[0].rounds[0].heats[0].arena").entity(String.class).isEqualTo("A1")
                .path("competition.categories[0].rounds[0].heats[0].start").entity(String.class)
                .isEqualTo(day + "T09:00:00")
                .path("competition.categories[0].rounds[0].heats[0].end").entity(String.class)
                .isEqualTo(day + "T09:15:00")
                .path("competition.categories[0].rounds[0].heats[0].judges").entityList(String.class)
                .containsExactly("J1", "J2");
    }

    // ---- AC-11: error parcial -------------------------------------------------------------------------

    @Test
    void aCategoryWithoutStandingsIsNullWithAnErrorWhileTheRestOfTheQueryArrives() {
        GraphQlEditions.Edition edition = editions.create("Partial Cup");
        TeamId alpha = editions.registerEligible(edition, "Alpha Bots");
        editions.capture(editions.scheduleRound(edition, 1, List.of(alpha)), alpha, 1, 4);
        editions.generateStandings(edition);

        graphQl.document("""
                        query ($id: ID!) {
                          competition(id: $id) { name categories { name standings { revision entries { team { name } } } } }
                        }""")
                .variable("id", edition.competitionId().value())
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && "NOT_FOUND".equals(error.getExtensions().get("code"))
                        && List.of("competition", "categories", 1, "standings").equals(error.getParsedPath()))
                .verify()
                .path("competition.name").entity(String.class).isEqualTo("Partial Cup")
                .path("competition.categories[*].name").entityList(String.class).containsExactly("Junior", "Senior")
                .path("competition.categories[0].standings.revision").entity(Integer.class).isEqualTo(1)
                .path("competition.categories[0].standings.entries[0].team.name").entity(String.class)
                .isEqualTo("Alpha Bots")
                .path("competition.categories[1].standings").valueIsNull();
    }
}
