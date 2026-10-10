package com.dps.roboleague.domain.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.ranking.aggregation.BestAttempt;
import com.dps.roboleague.domain.ranking.aggregation.SumOfAttempts;
import com.dps.roboleague.domain.ranking.rule.HighestSingleRunTiebreak;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.ScoringRuleCode;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class BestRoundsSelectionTest {

    private static final ChallengeId RESCUE = ChallengeId.of("RESCUE");
    private static final ChallengeId SPRINT = ChallengeId.of("SPRINT");
    private static final RoundStatus COUNTED = RoundStatus.COUNTED;
    private static final RoundStatus DISCARDED = RoundStatus.DISCARDED;

    private int runSequence;

    @Test
    void countsTheBestThreeOfFiveRoundsAndExplainsWhyTheOthersAreDiscarded() {
        TeamRounds rounds = rescueRounds(55, 30, 70, 45, 40);

        ScoreExplanation explanation = rounds.explain(new BestAttempt(), rescueCounting(3, 5));

        ScoreSubtotal selection = explanation.subtotals().get(1);
        assertEquals(Points.of(170), explanation.total());
        assertEquals("BEST_ROUNDS", selection.code());
        assertEquals("the best 3 of 5 rounds of challenge RESCUE", selection.description());
        assertEquals(Points.of(170), selection.points());
        assertEquals(List.of(COUNTED, DISCARDED, COUNTED, COUNTED, DISCARDED), statusesOf(selection));
        assertEquals(List.of(1, 2, 3, 4, 5), ordinalsOf(selection));
        assertEquals(List.of(Points.of(55), Points.of(30), Points.of(70), Points.of(45), Points.of(40)),
                selection.rounds().stream().map(RoundOutcome::points).toList());
        assertEquals("outside the best 3", selection.rounds().get(1).reason());
        assertEquals("outside the best 3", selection.rounds().get(4).reason());
        assertEquals("among the best 3", selection.rounds().getFirst().reason());
    }

    @Test
    void aTieAtTheCutKeepsTheRoundWithTheLowerOrdinal() {
        ScoreSubtotal selection = rescueRounds(55, 30, 70, 45, 45)
                .explain(new BestAttempt(), rescueCounting(3, 5)).subtotals().get(1);

        assertEquals(Points.of(170), selection.points());
        assertEquals(List.of(COUNTED, DISCARDED, COUNTED, COUNTED, DISCARDED), statusesOf(selection));
        assertEquals("tied with round 4, the lower ordinal wins", selection.rounds().get(4).reason());
        assertEquals("outside the best 3", selection.rounds().get(1).reason());
    }

    @Test
    void aTeamWithFewerRoundsThanNAddsUpEveryRoundItPlayed() {
        ScoreSubtotal selection = rescueRounds(55, 30)
                .explain(new BestAttempt(), rescueCounting(3, 5)).subtotals().get(1);

        assertEquals(List.of(COUNTED, COUNTED), statusesOf(selection));
        assertEquals(Points.of(85), selection.points());
    }

    @Test
    void anUnplayedRoundNeitherCountsAsZeroNorTakesAPlaceAmongTheBest() {
        TeamRounds rounds = new TeamRounds(List.of(round(1, RESCUE, 40), round(3, RESCUE, 10)));

        ScoreSubtotal selection = rounds.explain(new BestAttempt(), rescueCounting(2, 3)).subtotals().get(1);

        assertEquals(List.of(1, 3), ordinalsOf(selection));
        assertEquals(Points.of(50), selection.points());
    }

    @Test
    void theAttemptPolicyDecidesTheScoreOfEachRound() {
        TeamRounds rounds = new TeamRounds(List.of(round(1, RESCUE, 30, 40), round(2, RESCUE, 60)));

        ScoreSubtotal byBest = rounds.explain(new BestAttempt(), rescueCounting(1, 2)).subtotals().get(1);
        ScoreSubtotal bySum = rounds.explain(new SumOfAttempts(), rescueCounting(1, 2)).subtotals().get(1);

        assertEquals(List.of(Points.of(40), Points.of(60)), pointsOf(byBest));
        assertEquals(List.of(DISCARDED, COUNTED), statusesOf(byBest));
        assertEquals(Points.of(60), byBest.points());
        assertEquals(List.of(Points.of(70), Points.of(60)), pointsOf(bySum));
        assertEquals(List.of(COUNTED, DISCARDED), statusesOf(bySum));
        assertEquals(Points.of(70), bySum.points());
    }

    @Test
    void theSelectionOfRoundsCanChangeTheWinner() {
        List<StandingEntry> standings = new RankingService().rank(List.of(
                summary("ALPHA", rescueRounds(50, 50, 0), new BestAttempt(), rescueCounting(2, 3)),
                summary("BETA", rescueRounds(60, 30, 30), new BestAttempt(), rescueCounting(2, 3))), List.of());
        List<StandingEntry> everyRound = new RankingService().rank(List.of(
                summary("ALPHA", rescueRounds(50, 50, 0), new SumOfAttempts(), noBestRounds()),
                summary("BETA", rescueRounds(60, 30, 30), new SumOfAttempts(), noBestRounds())), List.of());

        assertEquals(List.of("ALPHA", "BETA"), standings.stream().map(entry -> entry.teamId().value()).toList());
        assertEquals(Points.of(100), standings.getFirst().totalPoints());
        assertEquals(Points.of(90), standings.get(1).totalPoints());
        assertEquals(Points.of(100), standings.getFirst().explanation().total());
        assertEquals("BETA", everyRound.getFirst().teamId().value());
        assertEquals(Points.of(120), everyRound.getFirst().totalPoints());
        assertEquals(Points.of(100), everyRound.get(1).totalPoints());
    }

    @Test
    void aCategoryWithConfiguredAndUnconfiguredChallengesAddsUpBothSubtotals() {
        TeamRounds rounds = new TeamRounds(List.of(round(1, RESCUE, 10), round(2, SPRINT, 20), round(3, RESCUE, 30),
                round(4, SPRINT, 35), round(5, RESCUE, 20)));

        ScoreExplanation explanation = rounds.explain(new BestAttempt(), rescueCounting(2, 3));

        assertEquals(List.of(BestAttempt.CODE, BestRounds.CODE), codesOf(explanation));
        assertEquals(Points.of(35), explanation.subtotals().getFirst().points());
        assertEquals(new BestAttempt().description(), explanation.subtotals().getFirst().description());
        assertTrue(explanation.subtotals().getFirst().rounds().isEmpty());
        assertEquals(Points.of(50), explanation.subtotals().get(1).points());
        assertEquals(Points.of(85), explanation.total());
    }

    @Test
    void withoutBestRoundsTheTotalIsTheAttemptPolicyOverEveryRun() {
        TeamRounds rounds = new TeamRounds(List.of(round(1, RESCUE, 30, 50), round(2, SPRINT, 40)));

        ScoreExplanation explanation = rounds.explain(new SumOfAttempts(), noBestRounds());

        assertEquals(List.of(SumOfAttempts.CODE), codesOf(explanation));
        assertEquals(rounds.runs(new SumOfAttempts()).aggregatedPoints(), explanation.total());
        assertEquals(Points.of(120), explanation.total());
    }

    @Test
    void theAttemptPolicySubtotalIsShownWithZeroWhenEveryChallengeCountsBestRounds() {
        ScoreExplanation explanation = rescueRounds(55, 30).explain(new BestAttempt(), rescueCounting(1, 2));

        assertEquals(List.of(BestAttempt.CODE, BestRounds.CODE), codesOf(explanation));
        assertEquals(Points.ZERO, explanation.subtotals().getFirst().points());
        assertEquals(Points.of(55), explanation.total());
    }

    @Test
    void negativeRoundScoresCountWhenTheyAreAmongTheBest() {
        ScoreSubtotal fewerThanN = rescueRounds(-5, -10).explain(new BestAttempt(), rescueCounting(2, 3))
                .subtotals().get(1);
        ScoreSubtotal moreThanN = rescueRounds(-5, -10, 0).explain(new BestAttempt(), rescueCounting(2, 3))
                .subtotals().get(1);

        assertEquals(Points.of(-15), fewerThanN.points());
        assertEquals(List.of(COUNTED, DISCARDED, COUNTED), statusesOf(moreThanN));
        assertEquals(Points.of(-5), moreThanN.points());
    }

    @Test
    void theSelectionIsTheSameWhateverTheOrderInWhichRoundsAreCollected() {
        List<PlayedRound> collected = new ArrayList<>(List.of(round(1, RESCUE, 45), round(2, RESCUE, 70),
                round(3, RESCUE, 45)));
        ScoreExplanation inOrder = new TeamRounds(collected).explain(new BestAttempt(), rescueCounting(2, 3));

        ScoreExplanation reversed = new TeamRounds(collected.reversed())
                .explain(new BestAttempt(), rescueCounting(2, 3));

        assertEquals(inOrder, reversed);
        assertEquals(List.of(COUNTED, COUNTED, DISCARDED), statusesOf(reversed.subtotals().get(1)));
    }

    @Test
    void tiebreaksStillLookAtTheRunsOfDiscardedRounds() {
        TeamRounds alphaRounds = new TeamRounds(List.of(round(1, RESCUE, 30, 40), round(2, RESCUE, 60)));
        TeamRounds betaRounds = new TeamRounds(List.of(round(1, RESCUE, 50, 20)));
        TeamScoreSummary alpha = new TeamScoreSummary(TeamId.of("ALPHA"), alphaRounds.runs(new SumOfAttempts()),
                alphaRounds.explain(new SumOfAttempts(), rescueCounting(1, 2)));
        TeamScoreSummary beta = new TeamScoreSummary(TeamId.of("BETA"), betaRounds.runs(new SumOfAttempts()),
                betaRounds.explain(new SumOfAttempts(), rescueCounting(1, 2)));

        List<StandingEntry> standings = new RankingService().rank(List.of(beta, alpha),
                List.of(new HighestSingleRunTiebreak()));

        assertEquals(DISCARDED, alpha.explanation().subtotals().get(1).rounds().get(1).status());
        assertEquals(Optional.of(Points.of(60)), alpha.bestRunPoints());
        assertEquals(List.of(Points.of(70), Points.of(70)), standings.stream().map(StandingEntry::totalPoints).toList());
        assertEquals(List.of("ALPHA", "BETA"), standings.stream().map(entry -> entry.teamId().value()).toList());
        assertEquals(HighestSingleRunTiebreak.CODE, standings.get(1).appliedTiebreaks().getFirst().code());
    }

    @Test
    void aStandingEntryKeepsAnExplanationThatAddsUpToItsTotal() {
        ScoreExplanation explanation = rescueRounds(55, 30).explain(new BestAttempt(), rescueCounting(1, 2));

        StandingEntry entry = new StandingEntry(1, TeamId.of("ALPHA"), Points.of(55), List.of(), explanation);

        assertEquals(explanation, entry.explanation());
        assertThrows(InvalidValueException.class,
                () -> new StandingEntry(1, TeamId.of("ALPHA"), Points.of(85), List.of(), explanation));
        assertThrows(NullPointerException.class,
                () -> new StandingEntry(1, TeamId.of("ALPHA"), Points.of(55), List.of(), null));
    }

    @Test
    void aStandingEntryWithoutBreakdownStatesItsTotalAsTheOnlySubtotal() {
        StandingEntry entry = new StandingEntry(1, TeamId.of("ALPHA"), Points.of(60), List.of());

        assertEquals(List.of(ScoreExplanation.STATED_TOTAL), codesOf(entry.explanation()));
        assertEquals(Points.of(60), entry.explanation().total());
    }

    @Test
    void aRoundCountsAsPlayedOnlyWithAttemptsOfItsOwnChallenge() {
        RoundOrdinal first = RoundOrdinal.of(1);

        assertThrows(InvalidValueException.class, () -> new PlayedRound(first, RESCUE, List.of()));
        assertThrows(InvalidValueException.class, () -> new PlayedRound(first, SPRINT, List.of(run(RESCUE, 10))));
        assertThrows(NullPointerException.class, () -> new PlayedRound(null, RESCUE, List.of(run(RESCUE, 10))));
        assertThrows(NullPointerException.class, () -> new PlayedRound(first, null, List.of(run(RESCUE, 10))));
    }

    @Test
    void aRoundIsScoredOnlyOnceForTheTeam() {
        RoundScore score = new RoundScore(RoundOrdinal.of(1), Points.of(10));

        assertThrows(InvalidValueException.class, () -> new RoundScores(List.of(score, score)));
        assertThrows(NullPointerException.class, () -> new RoundScore(null, Points.of(10)));
        assertThrows(NullPointerException.class, () -> new RoundScore(RoundOrdinal.of(1), null));
    }

    @Test
    void anOutcomeRequiresItsRoundPointsStatusAndReason() {
        RoundOrdinal first = RoundOrdinal.of(1);

        assertThrows(InvalidValueException.class, () -> new RoundOutcome(first, Points.ZERO, COUNTED, null));
        assertThrows(InvalidValueException.class, () -> new RoundOutcome(first, Points.ZERO, COUNTED, " "));
        assertThrows(NullPointerException.class, () -> new RoundOutcome(null, Points.ZERO, COUNTED, "reason"));
        assertThrows(NullPointerException.class, () -> new RoundOutcome(first, null, COUNTED, "reason"));
        assertThrows(NullPointerException.class, () -> new RoundOutcome(first, Points.ZERO, null, "reason"));
    }

    @Test
    void onlyCountedRoundsContributeTheirPoints() {
        assertEquals(Points.of(45), COUNTED.contributionOf(Points.of(45)));
        assertEquals(Points.ZERO, DISCARDED.contributionOf(Points.of(45)));
    }

    @Test
    void aSubtotalMustAddUpTheRoundsItCounts() {
        RoundOutcome counted = new RoundOutcome(RoundOrdinal.of(1), Points.of(40), COUNTED, "among the best 1");
        RoundOutcome discarded = new RoundOutcome(RoundOrdinal.of(2), Points.of(30), DISCARDED, "outside the best 1");

        assertEquals(Points.of(40), ScoreSubtotal.ofRounds("BEST_ROUNDS", "best", List.of(counted, discarded))
                .points());
        assertThrows(InvalidValueException.class,
                () -> new ScoreSubtotal("BEST_ROUNDS", "best", List.of(counted, discarded), Points.of(70)));
        assertThrows(InvalidValueException.class, () -> ScoreSubtotal.of(null, "description", Points.ZERO));
        assertThrows(InvalidValueException.class, () -> ScoreSubtotal.of(" ", "description", Points.ZERO));
        assertThrows(InvalidValueException.class, () -> ScoreSubtotal.of("CODE", null, Points.ZERO));
        assertThrows(InvalidValueException.class, () -> ScoreSubtotal.of("CODE", " ", Points.ZERO));
        assertThrows(NullPointerException.class, () -> ScoreSubtotal.of("CODE", "description", null));
    }

    @Test
    void anExplanationRequiresAtLeastOneSubtotal() {
        assertThrows(InvalidValueException.class, () -> new ScoreExplanation(List.of()));
    }

    @Test
    void aSummaryRequiresTheExplanationOfItsTotal() {
        TeamRuns runs = rescueRounds(10).runs(new BestAttempt());

        assertThrows(NullPointerException.class, () -> new TeamScoreSummary(TeamId.of("ALPHA"), runs, null));
    }

    private TeamScoreSummary summary(String teamId, TeamRounds rounds, AttemptAggregation aggregation,
            Function<ChallengeId, Optional<BestRounds>> bestRoundsOf) {
        return new TeamScoreSummary(TeamId.of(teamId), rounds.runs(aggregation),
                rounds.explain(aggregation, bestRoundsOf));
    }

    private static Function<ChallengeId, Optional<BestRounds>> rescueCounting(int counted, int outOf) {
        Map<ChallengeId, BestRounds> configured = Map.of(RESCUE, BestRounds.of(counted, outOf));
        return challengeId -> Optional.ofNullable(configured.get(challengeId));
    }

    private static Function<ChallengeId, Optional<BestRounds>> noBestRounds() {
        return challengeId -> Optional.empty();
    }

    private TeamRounds rescueRounds(int... roundPoints) {
        List<PlayedRound> rounds = new ArrayList<>();
        for (int index = 0; index < roundPoints.length; index++) {
            rounds.add(round(index + 1, RESCUE, roundPoints[index]));
        }
        return new TeamRounds(rounds);
    }

    private PlayedRound round(int ordinal, ChallengeId challengeId, int... attemptPoints) {
        return new PlayedRound(RoundOrdinal.of(ordinal), challengeId,
                Arrays.stream(attemptPoints).mapToObj(points -> run(challengeId, points)).toList());
    }

    private ScoredRun run(ChallengeId challengeId, int points) {
        runSequence++;
        return new ScoredRun(RunId.of("RUN-" + runSequence), challengeId, MeasurementSet.empty(),
                new ScoreBreakdown(List.of(ScoreContribution.earned(ScoringRuleCode.of("CHALLENGE"), "base score",
                        Points.of(points)))));
    }

    private static List<RoundStatus> statusesOf(ScoreSubtotal subtotal) {
        return subtotal.rounds().stream().map(RoundOutcome::status).toList();
    }

    private static List<Integer> ordinalsOf(ScoreSubtotal subtotal) {
        return subtotal.rounds().stream().map(outcome -> outcome.ordinal().value()).toList();
    }

    private static List<Points> pointsOf(ScoreSubtotal subtotal) {
        return subtotal.rounds().stream().map(RoundOutcome::points).toList();
    }

    private static List<String> codesOf(ScoreExplanation explanation) {
        return explanation.subtotals().stream().map(ScoreSubtotal::code).toList();
    }
}
