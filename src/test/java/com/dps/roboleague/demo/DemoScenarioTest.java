package com.dps.roboleague.demo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.FindAuditTrail;
import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.ranking.AppliedTiebreak;
import com.dps.roboleague.domain.ranking.RoundOutcome;
import com.dps.roboleague.domain.ranking.RoundStatus;
import com.dps.roboleague.domain.ranking.ScoreSubtotal;
import com.dps.roboleague.domain.ranking.StandingEntry;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.ranking.rule.FastestMetricTiebreak;
import com.dps.roboleague.domain.ranking.rule.FewestPenaltiesTiebreak;
import com.dps.roboleague.domain.ranking.rule.HighestSingleRunTiebreak;
import com.dps.roboleague.domain.scoring.BonusCap;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.infrastructure.config.RoboLeagueCompositionRoot;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemoScenarioTest {

    private static final CategoryId CATEGORY = CategoryId.of("CATEGORY-1");
    private static final TeamId KAPPA = TeamId.of("TEAM-1");
    private static final TeamId DELTA = TeamId.of("TEAM-2");
    private static final TeamId OMEGA = TeamId.of("TEAM-3");
    private static final TeamId SIGMA = TeamId.of("TEAM-4");

    private final RoboLeagueCompositionRoot module = RoboLeagueCompositionRoot.inMemory(TestEdition.fixedClock());

    @BeforeEach
    void runTheDemo() {
        assertDoesNotThrow(() -> new DemoScenario(module).run());
    }

    @Test
    void theCompositionRootWiresEveryUseCaseOfTheEdition() {
        List<AuditAction> actions = TestEdition.actionsOf(
                module.findAuditTrailUseCase().execute(new FindAuditTrail.Command(CATEGORY)));
        assertTrue(actions.containsAll(List.of(AuditAction.STANDINGS_GENERATED, AuditAction.STANDINGS_PUBLISHED,
                AuditAction.STANDINGS_RECALCULATED)));
    }

    @Test
    void thePublishedStandingsSeparateFourTiedTeamsWithThreeChainedTiebreaks() {
        Standings published = history().getFirst();

        assertTrue(published.isFinal());
        assertEquals(List.of(KAPPA, DELTA, OMEGA, SIGMA), teamsOf(published));
        assertTrue(published.entries().stream().allMatch(entry -> entry.totalPoints().equals(Points.of("187.50"))));
        assertEquals(List.of(List.of(), List.of(HighestSingleRunTiebreak.CODE), List.of(FewestPenaltiesTiebreak.CODE),
                List.of(FastestMetricTiebreak.CODE)), published.entries().stream().map(this::tiebreakCodesOf).toList());
    }

    @Test
    void theRescueChallengeCapsTheSumOfTheBonusesAndExplainsTheTrim() {
        ScoreBreakdown kappa = rescueBreakdownOf("RUN-1");

        assertEquals(List.of(Points.of(15), Points.of(10), Points.of(10)), kappa.contributions().stream()
                .filter(contribution -> contribution.ruleCode().equals(ThresholdBonusRule.CODE))
                .map(ScoreContribution::points)
                .toList());
        assertEquals(Points.of(-10), kappa.totalFor(BonusCap.CODE));
        assertEquals(Points.of(25), kappa.totalOf(ContributionKind.BONUS));
        assertEquals(Points.of("93.50"), kappa.total());
        assertTrue(kappa.contributions().stream().anyMatch(contribution -> contribution.explanation()
                .equals("bonuses obtained 35.00 exceed the cap of 25.00: 10.00 trimmed")));
        List.of("RUN-2", "RUN-3", "RUN-4").forEach(run -> {
            assertEquals(Points.ZERO, rescueBreakdownOf(run).totalFor(BonusCap.CODE));
            assertEquals(Points.of(25), rescueBreakdownOf(run).totalOf(ContributionKind.BONUS));
        });
    }

    @Test
    void thePrecisionChallengeCountsTheBestTwoOfThreeRoundsWithATieAtTheCut() {
        ScoreSubtotal kappa = bestRoundsOf(history().getFirst(), KAPPA);

        assertEquals("the best 2 of 3 rounds of challenge PRECISION", kappa.description());
        assertEquals(List.of(RoundStatus.COUNTED, RoundStatus.COUNTED, RoundStatus.DISCARDED), statusesOf(kappa));
        assertEquals("tied with round 4, the lower ordinal wins", kappa.rounds().get(2).reason());
    }

    @Test
    void theAcceptedAppealBringsBackADiscardedRoundAndReordersTheStandings() {
        Standings published = history().getFirst();
        Standings recalculated = history().getLast();

        assertEquals(List.of(SIGMA, KAPPA, DELTA, OMEGA), teamsOf(recalculated));
        assertEquals(Points.of("192.70"), recalculated.entryFor(SIGMA).orElseThrow().totalPoints());
        assertEquals(List.of(RoundStatus.COUNTED, RoundStatus.DISCARDED, RoundStatus.COUNTED),
                statusesOf(bestRoundsOf(recalculated, SIGMA)));
        assertEquals(List.of(RoundStatus.COUNTED, RoundStatus.COUNTED, RoundStatus.DISCARDED),
                statusesOf(bestRoundsOf(published, SIGMA)));
    }

    private ScoreBreakdown rescueBreakdownOf(String run) {
        return module.calculateRunScoreUseCase().execute(new CalculateRunScore.Command(RunId.of(run))).breakdown();
    }

    private List<Standings> history() {
        return module.getStandingsUseCase()
                .execute(new GetStandings.Command(CompetitionId.of("COMPETITION-1"), CATEGORY)).history();
    }

    private List<TeamId> teamsOf(Standings standings) {
        return standings.entries().stream().map(StandingEntry::teamId).toList();
    }

    private List<String> tiebreakCodesOf(StandingEntry entry) {
        return entry.appliedTiebreaks().stream().map(AppliedTiebreak::code).toList();
    }

    private ScoreSubtotal bestRoundsOf(Standings standings, TeamId team) {
        return standings.entryFor(team).orElseThrow().explanation().subtotals().stream()
                .filter(subtotal -> subtotal.code().equals(BestRounds.CODE))
                .findFirst()
                .orElseThrow();
    }

    private List<RoundStatus> statusesOf(ScoreSubtotal subtotal) {
        return subtotal.rounds().stream().map(RoundOutcome::status).toList();
    }
}
