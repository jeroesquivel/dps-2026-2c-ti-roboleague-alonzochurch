package com.dps.roboleague.demo;

import com.dps.roboleague.domain.appeal.AppealWindow;
import com.dps.roboleague.domain.challenge.AttemptLimit;
import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.MetricDefinition;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricKind;
import com.dps.roboleague.domain.challenge.MetricUnit;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.eligibility.EligibilityRequirements;
import com.dps.roboleague.domain.eligibility.rule.AgeRangeRule;
import com.dps.roboleague.domain.eligibility.rule.RequiredDocumentsRule;
import com.dps.roboleague.domain.eligibility.rule.RobotClassRule;
import com.dps.roboleague.domain.eligibility.rule.RobotSpecificationRule;
import com.dps.roboleague.domain.eligibility.rule.TeamCompositionRule;
import com.dps.roboleague.domain.ranking.AttemptAggregation;
import com.dps.roboleague.domain.ranking.TiebreakRule;
import com.dps.roboleague.domain.ranking.aggregation.SumOfAttempts;
import com.dps.roboleague.domain.ranking.rule.FastestMetricTiebreak;
import com.dps.roboleague.domain.ranking.rule.FewestPenaltiesTiebreak;
import com.dps.roboleague.domain.ranking.rule.HighestSingleRunTiebreak;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.scoring.BonusCap;
import com.dps.roboleague.domain.scoring.BonusPoints;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.scoring.PenaltyDefinition;
import com.dps.roboleague.domain.scoring.PointsCap;
import com.dps.roboleague.domain.scoring.PointsDeducted;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.scoring.rule.PrecisionScoringRule;
import com.dps.roboleague.domain.scoring.rule.ResourceScoringRule;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.scoring.rule.TimeScoringRule;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.team.Dimensions;
import com.dps.roboleague.domain.team.DocumentType;
import com.dps.roboleague.domain.team.Weight;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class DemoRulebook {

    static final ChallengeId CHALLENGE_ID = ChallengeId.of("RESCUE");
    static final ChallengeId SPRINT_ID = ChallengeId.of("SPRINT");
    static final ChallengeId PRECISION_ID = ChallengeId.of("PRECISION");
    static final MetricKey TIME = MetricKey.of("TIME");
    static final MetricKey OBJECTIVES = MetricKey.of("OBJECTIVES");
    static final MetricKey ENERGY = MetricKey.of("ENERGY");
    static final MetricKey DESIGN = MetricKey.of("DESIGN");
    static final MetricKey CHECKPOINTS = MetricKey.of("CHECKPOINTS");
    static final MetricKey ACCURACY = MetricKey.of("ACCURACY");
    static final PenaltyCode RESTART = PenaltyCode.of("RESTART");
    static final PenaltyCode OUT_OF_BOUNDS = PenaltyCode.of("OUT_OF_BOUNDS");

    private DemoRulebook() {
    }

    static ChallengeSpec rescueChallenge() {
        List<MetricDefinition> metrics = List.of(
                MetricDefinition.required(TIME, MetricKind.TIME_SECONDS, MetricUnit.of("s")),
                MetricDefinition.required(OBJECTIVES, MetricKind.OBJECTIVE_COUNT, MetricUnit.of("objectives")),
                MetricDefinition.required(ENERGY, MetricKind.RESOURCE_UNITS, MetricUnit.of("mAh")),
                MetricDefinition.optional(DESIGN, MetricKind.JUDGE_CRITERION, MetricUnit.of("points")));
        return new ChallengeSpec(CHALLENGE_ID, "Rescue mission", metrics, scoringRules(), penalties(),
                AttemptLimit.of(2), Optional.empty(), Optional.of(BonusCap.of(25)));
    }

    static List<ScoringRule> scoringRules() {
        return List.of(
                new TimeScoringRule(TIME, Duration.ofSeconds(120), PointsRate.of("0.50"), PointsCap.of(30)),
                new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5),
                new JudgePanelScoringRule(DESIGN, PointsRate.of(1)),
                new ResourceScoringRule(ENERGY, MetricValue.of(50), PointsRate.of(1)),
                new ThresholdBonusRule(OBJECTIVES, ThresholdBonusRule.Comparison.AT_LEAST, MetricValue.of(5),
                        BonusPoints.of(15)),
                new ThresholdBonusRule(TIME, ThresholdBonusRule.Comparison.AT_MOST, MetricValue.of(110),
                        BonusPoints.of(10)),
                new ThresholdBonusRule(ENERGY, ThresholdBonusRule.Comparison.AT_MOST, MetricValue.of(48),
                        BonusPoints.of(10)));
    }

    static ChallengeSpec sprintChallenge() {
        List<MetricDefinition> metrics = List.of(
                MetricDefinition.required(TIME, MetricKind.TIME_SECONDS, MetricUnit.of("s")),
                MetricDefinition.required(CHECKPOINTS, MetricKind.OBJECTIVE_COUNT, MetricUnit.of("checkpoints")));
        List<ScoringRule> scoringRules = List.of(
                new TimeScoringRule(TIME, Duration.ofSeconds(60), PointsRate.of(1), PointsCap.of(20)),
                new ObjectiveScoringRule(CHECKPOINTS, PointsRate.of(5), 4));
        return new ChallengeSpec(SPRINT_ID, "Line sprint", metrics, scoringRules, List.of(), AttemptLimit.of(1));
    }

    static ChallengeSpec precisionChallenge() {
        List<MetricDefinition> metrics = List.of(
                MetricDefinition.required(ACCURACY, MetricKind.PRECISION_RATIO, MetricUnit.of("ratio")));
        return new ChallengeSpec(PRECISION_ID, "Precision shooting", metrics,
                List.of(new PrecisionScoringRule(ACCURACY, PointsCap.of(40))), List.of(), AttemptLimit.of(1),
                Optional.of(BestRounds.of(2, 3)));
    }

    static List<PenaltyDefinition> penalties() {
        return List.of(
                new PenaltyDefinition(RESTART, "manual restart", PointsDeducted.of(3)),
                new PenaltyDefinition(OUT_OF_BOUNDS, "robot out of bounds", PointsDeducted.of(5)));
    }

    static RulebookDraft rulebook() {
        return new RulebookDraft(List.of(rescueChallenge(), sprintChallenge(), precisionChallenge()),
                eligibilityRequirements(), attemptAggregation(), tiebreaks(), AppealWindow.of(Duration.ofHours(1)));
    }

    private static EligibilityRequirements eligibilityRequirements() {
        return EligibilityRequirements.of(
                new AgeRangeRule(),
                new TeamCompositionRule(2, 4, 18),
                new RobotClassRule(),
                new RobotSpecificationRule(Weight.ofKilograms("3.000"), new Dimensions(200, 200, 200)),
                new RequiredDocumentsRule(Set.of(DocumentType.PARENTAL_CONSENT, DocumentType.TECHNICAL_SHEET)));
    }

    private static AttemptAggregation attemptAggregation() {
        return new SumOfAttempts();
    }

    private static List<TiebreakRule> tiebreaks() {
        return List.of(new HighestSingleRunTiebreak(), new FewestPenaltiesTiebreak(), new FastestMetricTiebreak(TIME));
    }
}
