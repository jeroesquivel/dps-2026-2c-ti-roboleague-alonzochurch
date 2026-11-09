package com.dps.roboleague.adapter.in.graphql;

import com.dps.roboleague.adapter.in.web.run.RunScoreResponse;
import com.dps.roboleague.rules.scoring.BonusCap;
import com.dps.roboleague.rules.scoring.PointsRate;
import com.dps.roboleague.rules.scoring.rule.ObjectiveScoringRule;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.web.client.RestClient;

/** Etapa 6: raíces de equipo/ronda/corrida y el recorrido anidado hasta el desglose del puntaje. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Import(GraphQlEditions.class)
class RunGraphQlTest {

    @Autowired HttpGraphQlTester graphQl;
    @Autowired GraphQlEditions editions;
    @LocalServerPort int port;

    @Test
    void returnsTeamResultsGroupedAndOrderedByRoundWithEveryRunScore() {
        GraphQlEditions.Edition edition = editions.create("Results Cup");
        TeamId team = editions.registerEligible(edition, "Alpha Bots");
        RoundId second = editions.scheduleRound(edition, 2, List.of(team));
        RoundId first = editions.scheduleRound(edition, 1, List.of(team));
        editions.capture(second, team, 1, 3);
        RunId run = editions.capture(first, team, 1, 5);
        RunScoreResponse rest = RestClient.create("http://localhost:" + port).get()
                .uri("/runs/{runId}/score", run.value()).retrieve().body(RunScoreResponse.class);

        GraphQlTester.Response response = graphQl.document("""
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
                .variable("id", team.value()).execute();
        response
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
                .path("team.results[0].runs[0].score.total").entity(BigDecimal.class)
                .satisfies(value -> assertAmount(rest.total().toPlainString(), value))
                .path("team.results[0].runs[0].score.contributions[*].ruleCode").entityList(String.class)
                .satisfies(values -> {
                    List<String> expected = rest.contributions().stream()
                            .map(RunScoreResponse.ContributionResponse::ruleCode).toList();
                    if (!values.equals(expected)) throw new AssertionError("different contribution codes");
                })
                .path("team.results[0].runs[0].score.contributions[*].points").entityList(BigDecimal.class)
                .satisfies(values -> {
                    List<BigDecimal> expected = rest.contributions().stream()
                            .map(RunScoreResponse.ContributionResponse::points).toList();
                    if (values.size() != expected.size()) throw new AssertionError("different contribution count");
                    for (int index = 0; index < values.size(); index++) {
                        assertAmount(expected.get(index).toPlainString(), values.get(index));
                    }
                });
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
        GraphQlTester.Response completed = graphQl.document("""
                        query ($id: ID!) { run(id: $id) { score { completionStatus total
                          bySource { sources { source subtotal contributions { points } }
                                     acrossSources { subtotal contributions { points } } } } } }
                        """).variable("id", run.value()).execute();
        completed
                .path("run.score.completionStatus").entity(String.class).isEqualTo("COMPLETE")
                .path("run.score.bySource.sources[*].source").entityList(String.class)
                .containsExactly("AUTOMATIC", "JUDGES")
                .path("run.score.bySource.sources").entityList(Object.class).hasSize(2)
                .path("run.score.bySource.acrossSources.subtotal").entity(BigDecimal.class)
                .satisfies(value -> {
                    if (value.compareTo(BigDecimal.ZERO) != 0) throw new AssertionError("expected zero");
                });
        completed.path("run.score.total").entity(BigDecimal.class).satisfies(total ->
                completed.path("run.score.bySource.sources[*].subtotal").entityList(BigDecimal.class)
                        .satisfies(subtotals -> completed.path("run.score.bySource.acrossSources.subtotal")
                                .entity(BigDecimal.class).satisfies(across -> {
                                    BigDecimal recomposed = subtotals.stream().reduce(across, BigDecimal::add);
                                    assertAmount(total.toPlainString(), recomposed);
                                })));
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

    @Test
    void scoresEachRunWithItsOwnRulebookVersionAfterANewOneIsPublished() {
        GraphQlEditions.Edition edition = editions.create("Historical Rules Cup");
        TeamId team = editions.registerEligible(edition, "Alpha Bots");
        RunId underFirst = editions.capture(editions.scheduleRound(edition, 1, List.of(team)), team, 1, 5);
        editions.publishRulebook(edition, RescueEditionFixture.rulebook(RescueEditionFixture.challengeScoredBy(
                List.of(new ObjectiveScoringRule(RescueEditionFixture.OBJECTIVES, PointsRate.of(100), 5)))));
        RunId underSecond = editions.capture(editions.scheduleRound(edition, 2, List.of(team)), team, 1, 5);

        graphQl.document("""
                        query ($first: ID!, $second: ID!) {
                          first: run(id: $first) { score { rulebookVersion total contributions { ruleCode } } }
                          second: run(id: $second) { score { rulebookVersion total contributions { ruleCode } } }
                        }""")
                .variable("first", underFirst.value()).variable("second", underSecond.value()).execute()
                .path("first.score.rulebookVersion").entity(String.class).isEqualTo("v1")
                .path("first.score.total").entity(BigDecimal.class).satisfies(value -> assertAmount("85.75", value))
                .path("first.score.contributions[*].ruleCode").entityList(String.class)
                .containsExactly("TIME", "OBJECTIVES", "JUDGES", "RESOURCE", "BONUS", "PENALTIES")
                .path("second.score.rulebookVersion").entity(String.class).isEqualTo("v2")
                .path("second.score.total").entity(BigDecimal.class).satisfies(value -> assertAmount("500", value))
                .path("second.score.contributions[*].ruleCode").entityList(String.class)
                .containsExactly("OBJECTIVES", "PENALTIES")
                .path("first.score.total").entity(BigDecimal.class).satisfies(value -> {
                    RunScoreResponse rest = RestClient.create("http://localhost:" + port).get()
                            .uri("/runs/{runId}/score", underFirst.value()).retrieve().body(RunScoreResponse.class);
                    assertAmount(rest.total().toPlainString(), value);
                });
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        if (actual.compareTo(new BigDecimal(expected)) != 0) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
