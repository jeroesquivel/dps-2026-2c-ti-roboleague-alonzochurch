package com.dps.roboleague.support;

import com.dps.roboleague.enrollment.eligibility.EligibilityRequirements;
import com.dps.roboleague.enrollment.eligibility.rule.AgeRangeRule;
import com.dps.roboleague.enrollment.eligibility.rule.RequiredDocumentsRule;
import com.dps.roboleague.enrollment.eligibility.rule.RobotClassRule;
import com.dps.roboleague.enrollment.eligibility.rule.RobotSpecificationRule;
import com.dps.roboleague.enrollment.eligibility.rule.TeamCompositionRule;
import com.dps.roboleague.enrollment.team.Dimensions;
import com.dps.roboleague.enrollment.team.DocumentType;
import com.dps.roboleague.enrollment.team.Weight;
import com.dps.roboleague.rules.challenge.AttemptLimit;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.challenge.MetricDefinition;
import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricKind;
import com.dps.roboleague.rules.challenge.MetricUnit;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.ranking.AttemptAggregation;
import com.dps.roboleague.rules.ranking.BestRounds;
import com.dps.roboleague.rules.ranking.RoundSelection;
import com.dps.roboleague.rules.ranking.TiebreakRule;
import com.dps.roboleague.rules.ranking.aggregation.BestAttempt;
import com.dps.roboleague.rules.ranking.rule.FastestMetricTiebreak;
import com.dps.roboleague.rules.ranking.rule.FewestPenaltiesTiebreak;
import com.dps.roboleague.rules.ranking.rule.HighestSingleRunTiebreak;
import com.dps.roboleague.rules.rulebook.AppealWindow;
import com.dps.roboleague.rules.rulebook.RulebookDraft;
import com.dps.roboleague.rules.scoring.BonusPoints;
import com.dps.roboleague.rules.scoring.BonusPolicy;
import com.dps.roboleague.rules.scoring.PenaltyCode;
import com.dps.roboleague.rules.scoring.PenaltyDefinition;
import com.dps.roboleague.rules.scoring.PointsCap;
import com.dps.roboleague.rules.scoring.PointsDeducted;
import com.dps.roboleague.rules.scoring.PointsRate;
import com.dps.roboleague.rules.scoring.ScoringRule;
import com.dps.roboleague.rules.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.rules.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.rules.scoring.rule.ResourceScoringRule;
import com.dps.roboleague.rules.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.rules.scoring.rule.TimeScoringRule;
import com.dps.roboleague.shared.ChallengeId;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public final class RescueEditionFixture {

    public static final ChallengeId CHALLENGE_ID = ChallengeId.of("RESCUE");
    public static final MetricKey TIME = MetricKey.of("TIME");
    public static final MetricKey OBJECTIVES = MetricKey.of("OBJECTIVES");
    public static final MetricKey ENERGY = MetricKey.of("ENERGY");
    public static final MetricKey DESIGN = MetricKey.of("DESIGN");
    public static final PenaltyCode RESTART = PenaltyCode.of("RESTART");
    public static final PenaltyCode OUT_OF_BOUNDS = PenaltyCode.of("OUT_OF_BOUNDS");

    private RescueEditionFixture() {
    }

    public static ChallengeSpec rescueChallenge() {
        return challengeScoredBy(scoringRules());
    }

    public static ChallengeSpec challengeScoredBy(List<ScoringRule> scoringRules) {
        return rescueBuilder(CHALLENGE_ID, "Rescue mission", scoringRules).build();
    }

    public static ChallengeSpec rescueChallengeCountingBestRounds(BestRounds bestRounds) {
        return challenge(CHALLENGE_ID, "Rescue mission", scoringRules(), bestRounds);
    }

    public static ChallengeSpec challenge(ChallengeId id, String name, List<ScoringRule> scoringRules,
            RoundSelection roundSelection) {
        return rescueBuilder(id, name, scoringRules).roundSelection(roundSelection).build();
    }

    public static ChallengeSpec challengeCappingBonuses(List<ScoringRule> scoringRules,
            BonusPolicy bonusPolicy) {
        return rescueBuilder(CHALLENGE_ID, "Rescue mission", scoringRules).bonusPolicy(bonusPolicy).build();
    }

    private static ChallengeSpec.Builder rescueBuilder(ChallengeId id, String name, List<ScoringRule> scoringRules) {
        return ChallengeSpec.builder(id, name)
                .metrics(metrics())
                .scoringRules(scoringRules)
                .penalties(penalties())
                .maximumAttempts(AttemptLimit.of(2));
    }

    public static List<ScoringRule> scoringRulesWithThreeBonuses() {
        return List.of(
                new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5),
                new ThresholdBonusRule(OBJECTIVES, ThresholdBonusRule.Comparison.AT_LEAST, MetricValue.of(5),
                        BonusPoints.of(15)),
                new ThresholdBonusRule(TIME, ThresholdBonusRule.Comparison.AT_MOST, MetricValue.of(60),
                        BonusPoints.of(10)),
                new ThresholdBonusRule(ENERGY, ThresholdBonusRule.Comparison.AT_MOST, MetricValue.of(40),
                        BonusPoints.of(10)));
    }

    public static List<MetricDefinition> metrics() {
        return List.of(
                MetricDefinition.required(TIME, MetricKind.TIME_SECONDS, MetricUnit.of("s")),
                MetricDefinition.required(OBJECTIVES, MetricKind.OBJECTIVE_COUNT, MetricUnit.of("objectives")),
                MetricDefinition.required(ENERGY, MetricKind.RESOURCE_UNITS, MetricUnit.of("mAh")),
                MetricDefinition.optional(DESIGN, MetricKind.JUDGE_CRITERION, MetricUnit.of("points")));
    }

    public static List<ScoringRule> scoringRules() {
        return List.of(
                new TimeScoringRule(TIME, Duration.ofSeconds(120), PointsRate.of("0.50"), PointsCap.of(30)),
                new ObjectiveScoringRule(OBJECTIVES, PointsRate.of(10), 5),
                new JudgePanelScoringRule(DESIGN, PointsRate.of(1)),
                new ResourceScoringRule(ENERGY, MetricValue.of(50), PointsRate.of(1)),
                new ThresholdBonusRule(OBJECTIVES, ThresholdBonusRule.Comparison.AT_LEAST, MetricValue.of(5),
                        BonusPoints.of(15)));
    }

    public static List<PenaltyDefinition> penalties() {
        return List.of(
                new PenaltyDefinition(RESTART, "manual restart", PointsDeducted.of(3)),
                new PenaltyDefinition(OUT_OF_BOUNDS, "robot out of bounds", PointsDeducted.of(5)));
    }

    public static EligibilityRequirements eligibilityRequirements() {
        return EligibilityRequirements.of(
                new AgeRangeRule(),
                new TeamCompositionRule(2, 4, 18),
                new RobotClassRule(),
                new RobotSpecificationRule(Weight.ofKilograms("3.000"), new Dimensions(200, 200, 200)),
                new RequiredDocumentsRule(Set.of(DocumentType.PARENTAL_CONSENT, DocumentType.TECHNICAL_SHEET)));
    }

    public static AppealWindow appealWindow() {
        return AppealWindow.of(Duration.ofHours(1));
    }

    public static RulebookDraft rulebook() {
        return rulebook(rescueChallenge());
    }

    public static RulebookDraft rulebook(ChallengeSpec challenge) {
        return rulebook(challenge, attemptAggregation());
    }

    public static RulebookDraft rulebook(ChallengeSpec challenge, AttemptAggregation aggregation) {
        return rulebook(List.of(challenge), aggregation);
    }

    public static RulebookDraft rulebook(List<ChallengeSpec> challenges, AttemptAggregation aggregation) {
        return new RulebookDraft(challenges, eligibilityRequirements(), aggregation, tiebreaks(), appealWindow());
    }

    public static AttemptAggregation attemptAggregation() {
        return new BestAttempt();
    }

    public static List<TiebreakRule> tiebreaks() {
        return List.of(new HighestSingleRunTiebreak(), new FewestPenaltiesTiebreak(), new FastestMetricTiebreak(TIME));
    }
}
