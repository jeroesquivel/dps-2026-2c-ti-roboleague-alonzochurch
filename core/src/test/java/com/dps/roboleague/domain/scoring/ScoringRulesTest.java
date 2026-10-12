package com.dps.roboleague.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.scoring.rule.PenaltyScoringRule;
import com.dps.roboleague.domain.scoring.rule.PrecisionScoringRule;
import com.dps.roboleague.domain.scoring.rule.ResourceScoringRule;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.scoring.rule.TimeScoringRule;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.Points;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ScoringRulesTest {

    private static final MetricKey TIME = MetricKey.of("TIME");
    private static final MetricKey OBJECTIVES = MetricKey.of("OBJECTIVES");
    private static final MetricKey PRECISION = MetricKey.of("PRECISION");
    private static final MetricKey ENERGY = MetricKey.of("ENERGY");
    private static final MetricKey DESIGN = MetricKey.of("DESIGN");
    private static final PenaltyCode RESTART = PenaltyCode.of("RESTART");

    @Test
    void timeRuleRewardsEverySecondSavedAgainstTheReference() {
        TimeScoringRule rule = new TimeScoringRule(TIME, Duration.ofSeconds(120), PointsRate.of("0.50"), PointsCap.of(30));

        assertEquals(Points.of("12.25"), new ScoreBreakdown(rule.apply(measured(TIME, "95.5"))).total());
    }

    @Test
    void timeRuleGivesNoPointsWhenTheReferenceIsExceeded() {
        TimeScoringRule rule = new TimeScoringRule(TIME, Duration.ofSeconds(120), PointsRate.of("0.50"), PointsCap.of(30));

        assertEquals(Points.ZERO, new ScoreBreakdown(rule.apply(measured(TIME, "130"))).total());
    }

    @Test
    void timeRuleNeverExceedsItsMaximum() {
        TimeScoringRule rule = new TimeScoringRule(TIME, Duration.ofSeconds(120), PointsRate.of("0.50"), PointsCap.of(10));

        assertEquals(Points.of(10), new ScoreBreakdown(rule.apply(measured(TIME, "10"))).total());
    }

    @Test
    void objectiveRuleIgnoresObjectivesReportedAboveTheMaximum() {
        ObjectiveScoringRule rule = new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5);

        assertEquals(Points.of(50), new ScoreBreakdown(rule.apply(measured(OBJECTIVES, "7"))).total());
    }

    @Test
    void precisionRuleScalesTheMaximumByTheAchievedRatio() {
        PrecisionScoringRule rule = new PrecisionScoringRule(PRECISION, PointsCap.of(20));

        assertEquals(Points.of("15.00"), new ScoreBreakdown(rule.apply(measured(PRECISION, "0.75"))).total());
    }

    @Test
    void resourceRuleOnlyDeductsTheConsumptionAboveTheAllowance() {
        ResourceScoringRule rule = new ResourceScoringRule(ENERGY, MetricValue.of(50), PointsRate.of(1));

        assertEquals(Points.ZERO, new ScoreBreakdown(rule.apply(measured(ENERGY, "42"))).total());
        assertEquals(Points.of(-5), new ScoreBreakdown(rule.apply(measured(ENERGY, "55"))).total());
    }

    @Test
    void judgePanelRuleAveragesTheEvaluationsOfItsCriterion() {
        JudgePanelScoringRule rule = new JudgePanelScoringRule(DESIGN, PointsRate.of(2));
        ScoringContext context = new ScoringContext(MeasurementSet.empty(),
                JudgeEvaluations.of(evaluation("J1", 8), evaluation("J2", 9), evaluation("J3", 7)), List.of());

        assertEquals(Points.of(16), new ScoreBreakdown(rule.apply(context)).total());
    }

    @Test
    void penaltyRuleDeductsOnceForEachOccurrence() {
        PenaltyScoringRule rule = PenaltyScoringRule.of(
                List.of(new PenaltyDefinition(RESTART, "manual restart", PointsDeducted.of(3))));
        ScoringContext context = new ScoringContext(MeasurementSet.empty(), JudgeEvaluations.none(),
                List.of(new IncidentReport(RESTART, 2)));

        assertEquals(Points.of(-6), new ScoreBreakdown(rule.apply(context)).total());
    }

    @Test
    void bonusRuleIsGrantedOnlyWhenTheThresholdIsReached() {
        ThresholdBonusRule rule = new ThresholdBonusRule(OBJECTIVES, ThresholdBonusRule.Comparison.AT_LEAST,
                MetricValue.of(5), BonusPoints.of(15));

        assertEquals(Points.of(15), new ScoreBreakdown(rule.apply(measured(OBJECTIVES, "5"))).total());
        assertEquals(Points.ZERO, new ScoreBreakdown(rule.apply(measured(OBJECTIVES, "4"))).total());
    }

    static Stream<ScoringRule> everyRule() {
        return Stream.of(
                new TimeScoringRule(TIME, Duration.ofSeconds(120), PointsRate.of("0.50"), PointsCap.of(30)),
                new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5),
                new PrecisionScoringRule(PRECISION, PointsCap.of(20)),
                new ResourceScoringRule(ENERGY, MetricValue.of(50), PointsRate.of(1)),
                new JudgePanelScoringRule(DESIGN, PointsRate.of(1)),
                new ThresholdBonusRule(OBJECTIVES, ThresholdBonusRule.Comparison.AT_LEAST, MetricValue.of(5),
                        BonusPoints.of(15)),
                PenaltyScoringRule.of(List.of(new PenaltyDefinition(RESTART, "manual restart", PointsDeducted.of(3)))));
    }

    @ParameterizedTest
    @MethodSource("everyRule")
    void everyRuleExplainsTheAbsenceOfItsInputInsteadOfFailing(ScoringRule rule) {
        ScoreBreakdown breakdown = new ScoreBreakdown(rule.apply(ScoringContext.of(MeasurementSet.empty())));

        assertFalse(breakdown.contributions().isEmpty(), "a rule always emits at least one contribution");
        assertEquals(Points.ZERO, breakdown.total());
        assertTrue(breakdown.contributions().stream().noneMatch(contribution -> contribution.explanation().isBlank()));
    }

    @Test
    void anIncidentThatTheRulebookDoesNotDefineIsExplainedInsteadOfDeducted() {
        PenaltyScoringRule rule = PenaltyScoringRule.of(List.of());
        ScoringContext context = new ScoringContext(MeasurementSet.empty(), JudgeEvaluations.none(),
                List.of(IncidentReport.once(RESTART)));

        ScoreBreakdown breakdown = new ScoreBreakdown(rule.apply(context));

        assertEquals(Points.ZERO, breakdown.total());
        assertTrue(breakdown.contributions().getFirst().explanation().contains("is not defined"));
    }

    @Test
    void timeRuleRequiresAPositiveReference() {
        assertThrows(InvalidValueException.class,
                () -> new TimeScoringRule(TIME, Duration.ZERO, PointsRate.of("0.50"), PointsCap.of(30)));
        assertThrows(InvalidValueException.class,
                () -> new TimeScoringRule(TIME, Duration.ofSeconds(-1), PointsRate.of("0.50"), PointsCap.of(30)));
    }

    @Test
    void aResourcePenaltyCannotBeConfiguredToAddPoints() {
        assertThrows(InvalidValueException.class,
                () -> new ResourceScoringRule(ENERGY, MetricValue.of(50), PointsRate.of(-10)));
        assertThrows(InvalidValueException.class,
                () -> new ResourceScoringRule(ENERGY, MetricValue.of("-1"), PointsRate.of(1)));
    }

    @Test
    void weightsCapsAndBonusesCannotBeNegative() {
        assertThrows(InvalidValueException.class, () -> new JudgePanelScoringRule(DESIGN, PointsRate.of(-1)));
        assertThrows(InvalidValueException.class, () -> new PrecisionScoringRule(PRECISION, PointsCap.of(-20)));
        assertThrows(InvalidValueException.class, () -> new ThresholdBonusRule(OBJECTIVES,
                ThresholdBonusRule.Comparison.AT_LEAST, MetricValue.of(5), BonusPoints.of(-15)));
    }

    @Test
    void penaltyCatalogRejectsTheSameCodeTwice() {
        List<PenaltyDefinition> repeated = List.of(new PenaltyDefinition(RESTART, "manual restart", PointsDeducted.of(3)),
                new PenaltyDefinition(RESTART, "restart", PointsDeducted.of(5)));

        assertThrows(InvalidValueException.class, () -> PenaltyScoringRule.of(repeated));
    }

    @Test
    void everyRuleDeclaresTheMetricsItReads() {
        assertEquals(List.of(Set.of(TIME), Set.of(OBJECTIVES), Set.of(PRECISION), Set.of(ENERGY), Set.of(DESIGN),
                        Set.of(OBJECTIVES), Set.of()),
                everyRule().map(ScoringRule::referencedMetrics).toList());
    }

    @Test
    void penaltyContributionsNeverAddPoints() {
        ScoringContext context = new ScoringContext(MeasurementSet.empty()
                .with(TIME, MetricValue.of("200")).with(OBJECTIVES, MetricValue.of(0))
                .with(PRECISION, MetricValue.of("0")).with(ENERGY, MetricValue.of("90")),
                JudgeEvaluations.none(), List.of(new IncidentReport(RESTART, 3)));

        assertTrue(everyRule().flatMap(rule -> rule.apply(context).stream())
                .filter(contribution -> contribution.kind() == ContributionKind.PENALTY)
                .noneMatch(contribution -> contribution.points().compareTo(Points.ZERO) > 0));
    }

    private ScoringContext measured(MetricKey key, String amount) {
        return ScoringContext.of(MeasurementSet.empty().with(key, MetricValue.of(amount)));
    }

    private JudgeEvaluation evaluation(String judge, int score) {
        return new JudgeEvaluation(JudgeId.of(judge), DESIGN, JudgeScore.of(score));
    }
}
