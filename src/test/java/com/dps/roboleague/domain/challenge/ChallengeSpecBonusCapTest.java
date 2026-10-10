package com.dps.roboleague.domain.challenge;

import static com.dps.roboleague.support.RescueEditionFixture.ENERGY;
import static com.dps.roboleague.support.RescueEditionFixture.OBJECTIVES;
import static com.dps.roboleague.support.RescueEditionFixture.RESTART;
import static com.dps.roboleague.support.RescueEditionFixture.TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.scoring.BonusCap;
import com.dps.roboleague.domain.scoring.BonusPoints;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.ScoringRuleCode;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.support.RescueEditionFixture;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChallengeSpecBonusCapTest {

    private static final ThresholdBonusRule OBJECTIVES_BONUS = new ThresholdBonusRule(OBJECTIVES,
            ThresholdBonusRule.Comparison.AT_LEAST, MetricValue.of(5), BonusPoints.of(15));

    private final ChallengeSpec capped = cappedAt(25);
    private final ChallengeSpec uncapped = RescueEditionFixture.challengeCappingBonuses(
            RescueEditionFixture.scoringRulesWithThreeBonuses(), Optional.empty());

    @ParameterizedTest(name = "objectives {0}, time {1}, energy {2} and cap {3}: bonuses {4}, trim {5}")
    @CsvSource({
            "5, 55, 35, 25, 25.00, -10.00",
            "5, 55, 45, 25, 25.00, 0.00",
            "5, 70, 45, 0, 0.00, -15.00",
            "4, 70, 45, 25, 0.00, 0.00"})
    void appliesTheCapToTheSumOfTheBonusesOfARun(int objectives, String seconds, String energy, long cap,
            String applied, String trim) {
        ScoreBreakdown breakdown = cappedAt(cap).score(context(objectives, seconds, energy, List.of()));

        assertEquals(Points.of(applied), breakdown.totalOf(ContributionKind.BONUS));
        assertEquals(Points.of(trim), breakdown.totalFor(BonusCap.CODE));
    }

    @Test
    void trimsBonusesThatAreEachBelowTheCapWhenTheirSumExceedsIt() {
        ScoreBreakdown breakdown = capped.score(context(5, "55", "35", List.of()));

        assertEquals(List.of(Points.of(15), Points.of(10), Points.of(10)), originalBonusesOf(breakdown));
        assertEquals(Points.of(-10), breakdown.totalFor(BonusCap.CODE));
        assertEquals(Points.of(25), breakdown.totalOf(ContributionKind.BONUS));
    }

    @Test
    void trimsASingleBonusLargerThanTheCapWithoutRejectingTheChallenge() {
        ChallengeSpec challenge = RescueEditionFixture.challengeCappingBonuses(List.of(new ThresholdBonusRule(
                OBJECTIVES, ThresholdBonusRule.Comparison.AT_LEAST, MetricValue.of(5), BonusPoints.of(40))),
                Optional.of(BonusCap.of(25)));

        ScoreBreakdown breakdown = challenge.score(context(5, "70", "45", List.of()));

        assertEquals(Points.of(25), breakdown.totalOf(ContributionKind.BONUS));
        assertEquals(Points.of(-15), breakdown.totalFor(BonusCap.CODE));
    }

    @Test
    void leavesTheTotalUntouchedWhenTheBonusesReachExactlyTheCap() {
        ScoringContext context = context(5, "55", "45", List.of());

        assertEquals(uncapped.score(context).total(), capped.score(context).total());
    }

    @Test
    void neitherEarnedPointsNorPenaltiesAreAffectedByTheCap() {
        ScoringContext context = context(5, "55", "35", List.of(IncidentReport.once(RESTART)));

        ScoreBreakdown breakdown = capped.score(context);
        ScoreBreakdown withoutCap = uncapped.score(context);

        assertEquals(Points.of(72), breakdown.total());
        assertEquals(Points.of(50), breakdown.totalOf(ContributionKind.EARNED));
        assertEquals(Points.of(-3), breakdown.totalOf(ContributionKind.PENALTY));
        assertEquals(withoutCap.totalOf(ContributionKind.EARNED), breakdown.totalOf(ContributionKind.EARNED));
        assertEquals(withoutCap.totalOf(ContributionKind.PENALTY), breakdown.totalOf(ContributionKind.PENALTY));
    }

    @Test
    void placesTheTrimAfterTheRuleContributionsAndBeforeThePenalties() {
        ScoreBreakdown breakdown = capped.score(context(5, "55", "35", List.of(IncidentReport.once(RESTART))));

        assertEquals(List.of("OBJECTIVES", "BONUS", "BONUS", "BONUS", "BONUS_CAP", "PENALTIES"),
                breakdown.contributions().stream().map(contribution -> contribution.ruleCode().value()).toList());
        assertEquals("bonuses obtained 35.00 exceed the cap of 25.00: 10.00 trimmed",
                breakdown.contributions().get(4).explanation());
    }

    @Test
    void explainsTheCapEvenWhenNoBonusWasObtained() {
        ScoreBreakdown breakdown = capped.score(context(4, "70", "45", List.of()));

        assertEquals("bonuses obtained 0.00 are within the cap of 25.00: nothing trimmed", trimOf(breakdown)
                .explanation());
    }

    @Test
    void capsBonusesFromAnyScoringRuleRecognisedOnlyByTheirKind() {
        ChallengeSpec challenge = RescueEditionFixture.challengeCappingBonuses(
                List.of(OBJECTIVES_BONUS, new FlatBonusRule(Points.of(20))), Optional.of(BonusCap.of(25)));

        ScoreBreakdown breakdown = challenge.score(context(5, "70", "45", List.of()));

        assertEquals(List.of(Points.of(15), Points.of(20)), originalBonusesOf(breakdown));
        assertEquals(Points.of(-10), breakdown.totalFor(BonusCap.CODE));
        assertEquals(Points.of(25), breakdown.totalOf(ContributionKind.BONUS));
    }

    @Test
    void theBonusRulesEmitTheSameContributionsWhetherOrNotTheChallengeIsCapped() {
        ScoringContext context = context(5, "55", "35", List.of());

        List<ScoreContribution> byTheRule = OBJECTIVES_BONUS.apply(context);

        assertEquals(byTheRule.getFirst(), capped.score(context).contributions().get(1));
        assertEquals(byTheRule.getFirst(), uncapped.score(context).contributions().get(1));
    }

    @Test
    void aChallengeWithoutCapHasNoTrimContribution() {
        ScoreBreakdown breakdown = uncapped.score(context(5, "55", "35", List.of()));

        assertTrue(uncapped.bonusCap().isEmpty());
        assertTrue(RescueEditionFixture.rescueChallenge().bonusCap().isEmpty());
        assertTrue(breakdown.contributions().stream()
                .noneMatch(contribution -> contribution.ruleCode().equals(BonusCap.CODE)));
        assertEquals(Points.of(35), breakdown.totalOf(ContributionKind.BONUS));
    }

    @Test
    void rejectsANegativeCapAndRequiresAnExplicitlyEmptyConfiguration() {
        assertThrows(InvalidValueException.class, () -> cappedAt(-1));
        assertThrows(NullPointerException.class, () -> RescueEditionFixture.challengeCappingBonuses(
                RescueEditionFixture.scoringRulesWithThreeBonuses(), null));
    }

    private static ChallengeSpec cappedAt(long cap) {
        return RescueEditionFixture.challengeCappingBonuses(RescueEditionFixture.scoringRulesWithThreeBonuses(),
                Optional.of(BonusCap.of(cap)));
    }

    private static ScoringContext context(int objectives, String seconds, String energy,
            List<IncidentReport> incidents) {
        return new ScoringContext(MeasurementSet.empty()
                .with(OBJECTIVES, MetricValue.of(objectives))
                .with(TIME, MetricValue.of(seconds))
                .with(ENERGY, MetricValue.of(energy)), JudgeEvaluations.none(), incidents);
    }

    private static List<Points> originalBonusesOf(ScoreBreakdown breakdown) {
        return breakdown.contributions().stream()
                .filter(contribution -> contribution.kind() == ContributionKind.BONUS)
                .filter(contribution -> !contribution.ruleCode().equals(BonusCap.CODE))
                .map(ScoreContribution::points)
                .toList();
    }

    private static ScoreContribution trimOf(ScoreBreakdown breakdown) {
        return breakdown.contributions().stream()
                .filter(contribution -> contribution.ruleCode().equals(BonusCap.CODE))
                .findFirst()
                .orElseThrow();
    }

    private record FlatBonusRule(Points points) implements ScoringRule {

        private static final ScoringRuleCode CODE = ScoringRuleCode.of("FLAT_BONUS");

        @Override
        public List<ScoreContribution> apply(ScoringContext context) {
            return List.of(ScoreContribution.bonus(CODE, "flat bonus granted to every run", points));
        }

        @Override
        public Set<MetricKey> referencedMetrics() {
            return Set.of();
        }
    }
}
