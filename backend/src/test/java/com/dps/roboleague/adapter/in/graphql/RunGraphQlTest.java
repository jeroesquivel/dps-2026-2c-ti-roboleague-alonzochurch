package com.dps.roboleague.adapter.in.graphql;

import com.dps.roboleague.rules.scoring.BonusCap;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.ShowcaseFixture;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

/** Etapa 6: raíces de equipo/ronda/corrida y el recorrido anidado hasta el desglose del puntaje. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Import(GraphQlEditions.class)
class RunGraphQlTest {

    @Autowired HttpGraphQlTester graphQl;
    @Autowired GraphQlEditions editions;

    @Test
    void returnsTeamResultsGroupedAndOrderedByRoundWithEveryRunScore() {
        GraphQlEditions.Edition edition = editions.create("Results Cup");
        TeamId team = editions.registerEligible(edition, "Alpha Bots");
        RoundId second = editions.scheduleRound(edition, 2, List.of(team));
        RoundId first = editions.scheduleRound(edition, 1, List.of(team));
        editions.capture(second, team, 1, 3);
        RunId run = editions.capture(first, team, 1, 5);

        graphQl.document("""
                        query ($id: ID!) {
                          team(id: $id) {
                            name category { name }
                            results {
                              round { ordinal }
                              runs { id attemptNumber status round { ordinal } team { name }
                                     score { completionStatus missingSources rulebookVersion total
                                             contributions { ruleCode kind explanation points } } }
                            }
                          }
                        }""")
                .variable("id", team.value()).execute()
                .path("team.name").entity(String.class).isEqualTo("Alpha Bots")
                .path("team.category.name").entity(String.class).isEqualTo("Junior")
                .path("team.results[*].round.ordinal").entityList(Integer.class).containsExactly(1, 2)
                .path("team.results[0].runs[0].id").entity(String.class).isEqualTo(run.value())
                .path("team.results[0].runs[0].attemptNumber").entity(Integer.class).isEqualTo(1)
                .path("team.results[0].runs[0].status").entity(String.class).isEqualTo("CAPTURED")
                .path("team.results[0].runs[0].round.ordinal").entity(Integer.class).isEqualTo(1)
                .path("team.results[0].runs[0].team.name").entity(String.class).isEqualTo("Alpha Bots")
                .path("team.results[0].runs[0].score.completionStatus").entity(String.class).isEqualTo("COMPLETE")
                .path("team.results[0].runs[0].score.missingSources").entityList(String.class).hasSize(0)
                .path("team.results[0].runs[0].score.rulebookVersion").entity(String.class).isEqualTo("v1")
                .path("team.results[0].runs[0].score.total").entity(BigDecimal.class).satisfies(value -> {
                    if (value.signum() <= 0) throw new AssertionError("the score must be positive");
                })
                .path("team.results[0].runs[0].score.contributions").entityList(Object.class).hasSizeGreaterThan(0);
    }

    @Test
    void exposesRoundAndRunAsRootQueries() {
        GraphQlEditions.Edition edition = editions.create("Root Queries Cup");
        TeamId team = editions.registerEligible(edition, "Alpha Bots");
        RoundId round = editions.scheduleRound(edition, 1, List.of(team));
        RunId run = editions.capture(round, team, 1, 4);

        graphQl.document("""
                        query ($round: ID!, $run: ID!) {
                          round(id: $round) { id runs { id } }
                          run(id: $run) { id round { id } team { name } score { total } }
                        }""")
                .variable("round", round.value()).variable("run", run.value()).execute()
                .path("round.id").entity(String.class).isEqualTo(round.value())
                .path("round.runs[0].id").entity(String.class).isEqualTo(run.value())
                .path("run.round.id").entity(String.class).isEqualTo(round.value())
                .path("run.team.name").entity(String.class).isEqualTo("Alpha Bots");
    }

    @Test
    void aPendingMixedRunHasNoPartialScoreAndACompletedOneIsBrokenDownBySource() {
        GraphQlEditions.Edition edition = editions.create("Mixed Cup",
                ShowcaseFixture.rulebook(ShowcaseFixture.mixedShowcase()));
        TeamId team = editions.registerEligible(edition, "Alpha Bots");
        RoundId round = editions.scheduleRound(edition, 1, List.of(team), ShowcaseFixture.CHALLENGE_ID);
        RunId run = editions.registerAutomatic(round, team, 1);

        graphQl.document("""
                        query ($id: ID!) { run(id: $id) { score { completionStatus missingSources total
                          contributions { points } bonusCap { cap } bySource { sources { subtotal } } } } }
                        """).variable("id", run.value()).execute()
                .path("run.score.completionStatus").entity(String.class).isEqualTo("PENDING")
                .path("run.score.missingSources").entityList(String.class).containsExactly("JUDGES")
                .path("run.score.total").valueIsNull()
                .path("run.score.contributions").valueIsNull()
                .path("run.score.bonusCap").valueIsNull()
                .path("run.score.bySource").valueIsNull();

        editions.registerPanel(round, team, 1);
        graphQl.document("""
                        query ($id: ID!) { run(id: $id) { score { completionStatus total
                          bySource { sources { source subtotal contributions { points } }
                                     acrossSources { subtotal contributions { points } } } } } }
                        """).variable("id", run.value()).execute()
                .path("run.score.completionStatus").entity(String.class).isEqualTo("COMPLETE")
                .path("run.score.bySource.sources[*].source").entityList(String.class)
                .containsExactly("AUTOMATIC", "JUDGES")
                .path("run.score.bySource.sources").entityList(Object.class).hasSize(2)
                .path("run.score.bySource.acrossSources.subtotal").entity(BigDecimal.class)
                .satisfies(value -> {
                    if (value.compareTo(BigDecimal.ZERO) != 0) throw new AssertionError("expected zero");
                });
    }

    @Test
    void exposesTheAppliedBonusCap() {
        var challenge = RescueEditionFixture.challengeCappingBonuses(
                RescueEditionFixture.scoringRulesWithThreeBonuses(), BonusCap.of(10));
        GraphQlEditions.Edition edition = editions.create("Bonus Cup", RescueEditionFixture.rulebook(challenge));
        TeamId team = editions.registerEligible(edition, "Alpha Bots");
        RunId run = editions.capture(editions.scheduleRound(edition, 1, List.of(team)), team, 1, 5);

        graphQl.document("""
                        query ($id: ID!) { run(id: $id) { score { total bonusCap { obtained cap trimmed applied } } } }
                        """).variable("id", run.value()).execute()
                .path("run.score.bonusCap.obtained").entity(BigDecimal.class)
                .satisfies(value -> assertAmount("15", value))
                .path("run.score.bonusCap.cap").entity(BigDecimal.class)
                .satisfies(value -> assertAmount("10", value))
                .path("run.score.bonusCap.trimmed").entity(BigDecimal.class)
                .satisfies(value -> assertAmount("5", value))
                .path("run.score.bonusCap.applied").entity(BigDecimal.class)
                .satisfies(value -> assertAmount("10", value));
    }

    @Test
    void reportsNotFoundForAnUnknownTeam() {
        graphQl.document("{ team(id: \"TEAM-404\") { name } }").execute()
                .errors().expect(error -> error.getErrorType() == ErrorType.NOT_FOUND
                        && "NOT_FOUND".equals(error.getExtensions().get("code"))
                        && List.of("team").equals(error.getParsedPath())).verify()
                .path("team").valueIsNull();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        if (actual.compareTo(new BigDecimal(expected)) != 0) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
