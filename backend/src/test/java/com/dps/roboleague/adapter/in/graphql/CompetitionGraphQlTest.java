package com.dps.roboleague.adapter.in.graphql;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consulta de una competencia por GraphQL, por HTTP real y con los adaptadores en memoria. Cubre AC-1, AC-8, AC-9 y
 * AC-10 de {@code SPEC_api-graphql.md} y que GraphiQL no se publica fuera del perfil {@code dev}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Import(GraphQlEditions.class)
class CompetitionGraphQlTest {

    @Autowired HttpGraphQlTester graphQl;
    @Autowired GraphQlEditions editions;
    @LocalServerPort int port;

    // ---- AC-1: competencia con categorías --------------------------------------------------------------

    @Test
    void returnsOnlyTheRequestedFieldsOfACompetitionAndItsCategories() {
        GraphQlEditions.Edition edition = editions.create("National Open");

        graphQl.document("""
                        query ($id: ID!) { competition(id: $id) { name categories { name } } }""")
                .variable("id", edition.competitionId().value())
                .execute()
                .path("competition").entity(Map.class)
                .satisfies(competition -> assertEquals(Set.of("name", "categories"), competition.keySet()))
                .path("competition.name").entity(String.class).isEqualTo("National Open")
                .path("competition.categories[*].name").entityList(String.class).containsExactly("Junior", "Senior")
                .path("competition.categories[0]").entity(Map.class)
                .satisfies(category -> assertEquals(Set.of("name"), category.keySet()));
    }

    @Test
    void exposesTheCompetitionWithTheSameValuesAsTheRestApi() {
        GraphQlEditions.Edition edition = editions.create("Regional Cup");

        graphQl.document("""
                        query ($id: ID!) {
                          competition(id: $id) {
                            id name activeRulebookVersion period { start end }
                            categories { id name robotClass ageRange { minimumYears maximumYears } }
                          }
                        }""")
                .variable("id", edition.competitionId().value())
                .execute()
                .path("competition.id").entity(String.class).isEqualTo(edition.competitionId().value())
                .path("competition.activeRulebookVersion").entity(String.class).isEqualTo("v1")
                .path("competition.period.start").entity(String.class).isEqualTo(edition.period().start().toString())
                .path("competition.period.end").entity(String.class).isEqualTo(edition.period().end().toString())
                .path("competition.categories[*].id").entityList(String.class)
                .containsExactly(edition.junior().value(), edition.senior().value())
                .path("competition.categories[0].robotClass").entity(String.class).isEqualTo("RESCUE_BOT")
                .path("competition.categories[0].ageRange.minimumYears").entity(Integer.class).isEqualTo(12)
                .path("competition.categories[0].ageRange.maximumYears").entity(Integer.class).isEqualTo(17)
                .path("competition.categories[1].robotClass").entity(String.class).isEqualTo("SUMO");
    }

    // ---- AC-8: entidad no encontrada --------------------------------------------------------------------

    @Test
    void reportsAnUnknownCompetitionAsNotFoundWithThePathOfTheField() {
        graphQl.document("{ competition(id: \"COMPETITION-404\") { name } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && "NOT_FOUND".equals(error.getExtensions().get("code"))
                        && "competition".equals(error.getPath())
                        && error.getMessage().contains("COMPETITION-404"))
                .verify()
                .path("competition").valueIsNull();
    }

    // ---- AC-9: campo inexistente ------------------------------------------------------------------------

    @Test
    void rejectsAFieldThatIsNotInTheSchemaWithoutRunningAnyResolver() {
        graphQl.document("{ competition(id: \"COMPETITION-404\") { name birthDate } }")
                .execute()
                .errors()
                .satisfy(errors -> {
                    // Si el resolver se hubiera ejecutado, también habría un NOT_FOUND por la competencia inexistente.
                    assertEquals(1, errors.size(), errors.toString());
                    ResponseError error = errors.getFirst();
                    assertEquals(graphql.ErrorType.ValidationError, error.getErrorType());
                    assertTrue(error.getMessage().contains("birthDate"), error.getMessage());
                });
    }

    // ---- AC-10: valor inválido --------------------------------------------------------------------------

    @Test
    void reportsABlankIdAsAnInvalidValue() {
        graphQl.document("{ competition(id: \" \") { name } }")
                .execute()
                .errors()
                .expect(error -> error.getErrorType() == ErrorType.BAD_REQUEST
                        && "INVALID_VALUE".equals(error.getExtensions().get("code")))
                .verify()
                .path("competition").valueIsNull();
    }

    // ---- OQ-2: GraphiQL sólo en el perfil dev --------------------------------------------------------------

    @Test
    void doesNotServeTheGraphiqlConsoleOutsideTheDevProfile() {
        HttpStatusCode status = RestClient.create("http://localhost:" + port).get().uri("/graphiql")
                .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toBodilessEntity().getStatusCode();

        assertEquals(HttpStatus.NOT_FOUND, status);
    }
}
