package com.dps.roboleague.adapter.in.graphql;

import com.dps.roboleague.enrollment.port.in.FindTeamRegistration;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistrations;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.TeamId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Evidencia del tratamiento del N+1 (AC-12 y AC-13 de {@code SPEC_api-graphql.md}). Los puertos de equipos se
 * envuelven con decoradores que cuentan las llamadas: así se ve cuántas veces la API pide equipos al núcleo para
 * responder una consulta, sin mirar SQL ni usar un framework de mocks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Import(GraphQlEditions.class)
class TeamBatchLoadingTest {

    private static final String TABLE = """
            query ($competitionId: ID!, $categoryId: ID!) {
              standings(competitionId: $competitionId, categoryId: $categoryId) { entries { team { name } } }
            }""";

    /** El mismo equipo en dos niveles: en la tabla y en las corridas anidadas de ese equipo. */
    private static final String TABLE_AND_RUN_TEAMS = """
            query ($competitionId: ID!, $categoryId: ID!) {
              standings(competitionId: $competitionId, categoryId: $categoryId) {
                entries { team { name results { runs { team { name } } } } }
              }
            }""";

    @Autowired HttpGraphQlTester graphQl;
    @Autowired GraphQlEditions editions;
    @Autowired FindTeamRegistrations batch;
    @Autowired FindTeamRegistration single;
    @LocalServerPort int port;

    private GraphQlEditions.Edition edition;
    private Set<TeamId> teams;

    @BeforeEach
    void threeTeamsWithStandings() {
        edition = editions.create("Batch Cup");
        TeamId alpha = editions.registerEligible(edition, "Alpha Bots");
        TeamId delta = editions.registerEligible(edition, "Delta Bots");
        TeamId omega = editions.registerEligible(edition, "Omega Crew");
        RoundId round = editions.scheduleRound(edition, 1, List.of(alpha, delta, omega));
        editions.capture(round, alpha, 1, 5);
        editions.capture(round, delta, 1, 4);
        editions.capture(round, omega, 1, 3);
        editions.generateStandings(edition);
        teams = Set.of(alpha, delta, omega);
        counting(batch).reset();
        counting(single).reset();
    }

    // ---- AC-12: un lote por nivel ------------------------------------------------------------------------

    @Test
    void loadsTheTeamsOfEveryRowOfTheTableInASingleBatch() {
        query(TABLE).path("standings.entries[*].team.name").entityList(String.class)
                .containsExactly("Alpha Bots", "Delta Bots", "Omega Crew");

        assertEquals(List.of(teams), counting(batch).calls());
        assertEquals(0, counting(single).calls());
    }

    @Test
    void theRestApiAsksForTheTeamOfEachRowSeparately() {
        RestClient.create("http://localhost:" + port).get()
                .uri("/competitions/{competition}/categories/{category}/standings",
                        edition.competitionId().value(), edition.junior().value())
                .retrieve().toBodilessEntity();

        assertEquals(teams.size(), counting(single).calls());
        assertEquals(List.of(), counting(batch).calls());
    }

    // ---- AC-13: caché por petición ----------------------------------------------------------------------

    @Test
    void aTeamThatAppearsAgainInTheSameRequestIsNotLoadedTwice() {
        query(TABLE_AND_RUN_TEAMS).path("standings.entries[*].team.results[0].runs[0].team.name")
                .entityList(String.class).containsExactly("Alpha Bots", "Delta Bots", "Omega Crew");

        assertEquals(List.of(teams), counting(batch).calls());
        assertEquals(0, counting(single).calls());
    }

    @Test
    void theCacheDoesNotOutliveTheRequest() {
        query(TABLE);
        query(TABLE);

        assertEquals(List.of(teams, teams), counting(batch).calls());
    }

    // ---- soporte ----------------------------------------------------------------------------------------

    private GraphQlTester.Response query(String document) {
        return graphQl.document(document)
                .variable("competitionId", edition.competitionId().value())
                .variable("categoryId", edition.junior().value())
                .execute();
    }

    private static CountingFindTeamRegistrations counting(FindTeamRegistrations port) {
        return (CountingFindTeamRegistrations) port;
    }

    private static CountingFindTeamRegistration counting(FindTeamRegistration port) {
        return (CountingFindTeamRegistration) port;
    }

    /** Envuelve los puertos de equipos con los contadores; los que los usan reciben el decorador sin enterarse. */
    @TestConfiguration(proxyBeanMethods = false)
    static class CountingTeamPorts {

        @Bean
        static BeanPostProcessor countingTeamPorts() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof FindTeamRegistrations port) {
                        return new CountingFindTeamRegistrations(port);
                    }
                    if (bean instanceof FindTeamRegistration port) {
                        return new CountingFindTeamRegistration(port);
                    }
                    return bean;
                }
            };
        }
    }

    /** Decorador de {@link FindTeamRegistrations} que registra qué equipos pidió cada llamada. */
    static final class CountingFindTeamRegistrations implements FindTeamRegistrations {

        private final FindTeamRegistrations delegate;
        private final List<Set<TeamId>> calls = new CopyOnWriteArrayList<>();

        CountingFindTeamRegistrations(FindTeamRegistrations delegate) {
            this.delegate = delegate;
        }

        @Override
        public Map<TeamId, TeamRegistration> execute(Command command) {
            calls.add(command.teamIds());
            return delegate.execute(command);
        }

        List<Set<TeamId>> calls() {
            return List.copyOf(calls);
        }

        void reset() {
            calls.clear();
        }
    }

    /** Decorador de {@link FindTeamRegistration} que cuenta las llamadas de a un equipo. */
    static final class CountingFindTeamRegistration implements FindTeamRegistration {

        private final FindTeamRegistration delegate;
        private final AtomicInteger calls = new AtomicInteger();

        CountingFindTeamRegistration(FindTeamRegistration delegate) {
            this.delegate = delegate;
        }

        @Override
        public TeamRegistration execute(TeamId teamId) {
            calls.incrementAndGet();
            return delegate.execute(teamId);
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }
}
