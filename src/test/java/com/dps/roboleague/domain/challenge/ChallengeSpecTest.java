package com.dps.roboleague.domain.challenge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.scoring.BonusPoints;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluation;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.scoring.PenaltyDefinition;
import com.dps.roboleague.domain.scoring.PointsCap;
import com.dps.roboleague.domain.scoring.PointsDeducted;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.scoring.rule.TimeScoringRule;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RuleViolationException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChallengeSpecTest {

    private static final MetricKey TIME = MetricKey.of("TIME");
    private static final MetricKey OBJECTIVES = MetricKey.of("OBJECTIVES");
    private static final MetricKey DESIGN = MetricKey.of("DESIGN");
    private static final PenaltyCode RESTART = PenaltyCode.of("RESTART");
    private static final MetricDefinition TIME_METRIC = MetricDefinition.required(TIME, MetricKind.TIME_SECONDS,
            MetricUnit.of("s"));
    private static final MetricDefinition OBJECTIVES_METRIC = MetricDefinition.required(OBJECTIVES,
            MetricKind.OBJECTIVE_COUNT, MetricUnit.of("objectives"));
    private static final MetricDefinition DESIGN_METRIC = MetricDefinition.optional(DESIGN,
            MetricKind.JUDGE_CRITERION, MetricUnit.of("points"));
    private static final ScoringRule OBJECTIVES_RULE = new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5);
    private static final PenaltyDefinition RESTART_PENALTY = new PenaltyDefinition(RESTART, "manual restart",
            PointsDeducted.of(3));
    private static final Set<JudgeId> PANEL = Set.of(JudgeId.of("J1"), JudgeId.of("J2"));

    private final ChallengeSpec challenge = challengeWith(List.of(TIME_METRIC, OBJECTIVES_METRIC, DESIGN_METRIC),
            List.of(OBJECTIVES_RULE), List.of(RESTART_PENALTY));

    @Test
    void acceptsAMeasurementSetThatCoversEveryRequiredMetric() {
        assertDoesNotThrow(() -> challenge.validate(complete()));
    }

    @Test
    void rejectsAMeasurementSetWithoutARequiredMetric() {
        MeasurementSet incomplete = MeasurementSet.empty().with(TIME, MetricValue.of("95.5"));

        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> challenge.validate(incomplete));

        assertTrue(error.getMessage().contains("OBJECTIVES"));
    }

    @Test
    void rejectsMetricsThatTheChallengeDoesNotDefine() {
        MeasurementSet unexpected = complete().with(MetricKey.of("BATTERY"), MetricValue.of("10"));

        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> challenge.validate(unexpected));

        assertTrue(error.getMessage().contains("BATTERY"));
    }

    @Test
    void rejectsValuesThatDoNotMatchTheKindOfTheMetric() {
        MeasurementSet fractionalObjectives = complete().with(OBJECTIVES, MetricValue.of("3.5"));

        assertThrows(RuleViolationException.class, () -> challenge.validate(fractionalObjectives));
    }

    @Test
    void rejectsAttemptsBeyondTheConfiguredLimit() {
        assertDoesNotThrow(() -> challenge.requireAttemptWithinLimit(AttemptNumber.of(2)));
        assertThrows(RuleViolationException.class, () -> challenge.requireAttemptWithinLimit(AttemptNumber.of(3)));
    }

    @Test
    void acceptsIncidentsThatTheRulebookDefines() {
        assertDoesNotThrow(() -> challenge.validateIncidents(List.of(IncidentReport.once(RESTART))));
    }

    @Test
    void rejectsIncidentsThatTheRulebookDoesNotDefineWhenTheResultIsCaptured() {
        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> challenge.validateIncidents(List.of(IncidentReport.once(PenaltyCode.of("SABOTAGE")))));

        assertTrue(error.getMessage().contains("SABOTAGE"));
    }

    @Test
    void requiresAtLeastOneScoringRule() {
        InvalidValueException error = assertThrows(InvalidValueException.class,
                () -> challengeWith(List.of(TIME_METRIC), List.of(), List.of()));

        assertTrue(error.getMessage().contains("scoring rule"));
    }

    @Test
    void rejectsAMetricDeclaredTwiceEvenWithDifferentKinds() {
        MetricDefinition objectivesAsResource = MetricDefinition.optional(OBJECTIVES, MetricKind.RESOURCE_UNITS,
                MetricUnit.of("units"));

        InvalidValueException error = assertThrows(InvalidValueException.class,
                () -> challengeWith(List.of(OBJECTIVES_METRIC, objectivesAsResource), List.of(OBJECTIVES_RULE),
                        List.of()));

        assertTrue(error.getMessage().contains("metric OBJECTIVES is declared twice"));
    }

    @Test
    void rejectsAPenaltyDeclaredTwiceWhenTheChallengeIsConfiguredInsteadOfWhenItScores() {
        PenaltyDefinition harsherRestart = new PenaltyDefinition(RESTART, "manual restart", PointsDeducted.of(10));

        InvalidValueException error = assertThrows(InvalidValueException.class,
                () -> challengeWith(List.of(OBJECTIVES_METRIC), List.of(OBJECTIVES_RULE),
                        List.of(RESTART_PENALTY, harsherRestart)));

        assertTrue(error.getMessage().contains("penalty RESTART is declared twice"));
    }

    @Test
    void rejectsScoringRulesOverMetricsThatTheChallengeDoesNotDefine() {
        ScoringRule timeRule = new TimeScoringRule(TIME, Duration.ofSeconds(120), PointsRate.of("0.50"),
                PointsCap.of(30));

        InvalidValueException error = assertThrows(InvalidValueException.class,
                () -> challengeWith(List.of(OBJECTIVES_METRIC), List.of(OBJECTIVES_RULE, timeRule), List.of()));

        assertTrue(error.getMessage().contains("metric TIME"));
    }

    @Test
    void acceptsEvaluationsOfItsJudgeCriteriaByJudgesOfTheHeat() {
        JudgeEvaluations evaluations = JudgeEvaluations.of(evaluation("J1", DESIGN, 8), evaluation("J2", DESIGN, 9));

        assertDoesNotThrow(() -> challenge.validateEvaluations(evaluations, PANEL));
    }

    @Test
    void rejectsEvaluationsOfCriteriaThatAreNotJudgeCriteriaOfTheChallenge() {
        JudgeEvaluations undefined = JudgeEvaluations.of(evaluation("J1", MetricKey.of("STYLE"), 8));
        JudgeEvaluations measured = JudgeEvaluations.of(evaluation("J1", OBJECTIVES, 8));

        assertThrows(RuleViolationException.class, () -> challenge.validateEvaluations(undefined, PANEL));
        assertThrows(RuleViolationException.class, () -> challenge.validateEvaluations(measured, PANEL));
    }

    @Test
    void rejectsEvaluationsFromJudgesThatAreNotAssignedToTheHeat() {
        JudgeEvaluations evaluations = JudgeEvaluations.of(evaluation("J1", DESIGN, 8), evaluation("J9", DESIGN, 9));

        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> challenge.validateEvaluations(evaluations, PANEL));

        assertTrue(error.getMessage().contains("J9"));
    }

    @Test
    void scoreCombinesEveryScoringRuleWithTheChallengesOwnPenaltyCatalog() {
        ChallengeSpec multiRuleChallenge = challengeWith(List.of(TIME_METRIC, OBJECTIVES_METRIC),
                List.of(new TimeScoringRule(TIME, Duration.ofSeconds(120), PointsRate.of("0.50"), PointsCap.of(30)),
                        OBJECTIVES_RULE),
                List.of(RESTART_PENALTY));
        ScoringContext context = new ScoringContext(
                MeasurementSet.empty().with(TIME, MetricValue.of("95.5")).with(OBJECTIVES, MetricValue.of(4)),
                JudgeEvaluations.none(), List.of(IncidentReport.once(RESTART)));

        ScoreBreakdown breakdown = multiRuleChallenge.score(context);

        assertEquals(3, breakdown.contributions().size());
        assertEquals(Points.of("-3.00"), breakdown.totalOf(ContributionKind.PENALTY));
        assertEquals(Points.of("49.25"), breakdown.total());
    }

    @Test
    void scoreSeparatesWhatWasEarnedFromBonusesAndPenalties() {
        ChallengeSpec multiRuleChallenge = challengeWith(List.of(OBJECTIVES_METRIC),
                List.of(OBJECTIVES_RULE, new ThresholdBonusRule(OBJECTIVES, ThresholdBonusRule.Comparison.AT_LEAST,
                        MetricValue.of(5), BonusPoints.of(15))),
                List.of(RESTART_PENALTY));
        ScoringContext context = new ScoringContext(MeasurementSet.empty().with(OBJECTIVES, MetricValue.of(5)),
                JudgeEvaluations.none(), List.of(IncidentReport.once(RESTART)));

        ScoreBreakdown breakdown = multiRuleChallenge.score(context);

        assertEquals(Points.of("50.00"), breakdown.totalOf(ContributionKind.EARNED));
        assertEquals(Points.of("15.00"), breakdown.totalOf(ContributionKind.BONUS));
        assertEquals(Points.of("-3.00"), breakdown.totalOf(ContributionKind.PENALTY));
        assertEquals(Points.of("62.00"), breakdown.total());
    }

    @Test
    void aJudgeCriterionCanBeScoredOnlyIfTheChallengeDefinesIt() {
        ScoringRule judges = new JudgePanelScoringRule(DESIGN, PointsRate.of(1));

        assertDoesNotThrow(() -> challengeWith(List.of(DESIGN_METRIC), List.of(judges), List.of()));
        assertThrows(InvalidValueException.class,
                () -> challengeWith(List.of(OBJECTIVES_METRIC), List.of(judges), List.of()));
    }

    private static ChallengeSpec challengeWith(List<MetricDefinition> metrics, List<ScoringRule> rules,
            List<PenaltyDefinition> penalties) {
        return new ChallengeSpec(ChallengeId.of("RESCUE"), "Rescue mission", metrics, rules, penalties,
                AttemptLimit.of(2));
    }

    private static JudgeEvaluation evaluation(String judge, MetricKey criterion, long score) {
        return new JudgeEvaluation(JudgeId.of(judge), criterion, JudgeScore.of(score));
    }

    private MeasurementSet complete() {
        return MeasurementSet.empty()
                .with(TIME, MetricValue.of("95.5"))
                .with(OBJECTIVES, MetricValue.of(4));
    }
}
