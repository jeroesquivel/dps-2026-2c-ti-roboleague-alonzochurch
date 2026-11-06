package com.dps.roboleague.support;

import com.dps.roboleague.enrollment.eligibility.EligibilityVerdict;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.rules.challenge.AttemptLimit;
import com.dps.roboleague.rules.challenge.CaptureMode;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.challenge.MetricDefinition;
import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricKind;
import com.dps.roboleague.rules.challenge.MetricRequirement;
import com.dps.roboleague.rules.challenge.MetricUnit;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.challenge.MixedSources;
import com.dps.roboleague.rules.rulebook.RulebookDraft;
import com.dps.roboleague.rules.scoring.BonusPolicy;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.JudgeEvaluation;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.JudgeScore;
import com.dps.roboleague.rules.scoring.PenaltyCode;
import com.dps.roboleague.rules.scoring.PenaltyDefinition;
import com.dps.roboleague.rules.scoring.PointsCap;
import com.dps.roboleague.rules.scoring.PointsDeducted;
import com.dps.roboleague.rules.scoring.PointsRate;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.rules.scoring.ScoringRule;
import com.dps.roboleague.rules.scoring.ScoringRuleCode;
import com.dps.roboleague.rules.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.rules.scoring.rule.PrecisionScoringRule;
import com.dps.roboleague.rules.scoring.rule.TimeScoringRule;
import com.dps.roboleague.scheduling.Heat;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.shared.ArenaId;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.HeatId;
import com.dps.roboleague.shared.JudgeId;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RoundOrdinal;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.shared.TimeSlot;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class ShowcaseFixture {

    public static final ChallengeId CHALLENGE_ID = ChallengeId.of("SHOWCASE");
    public static final MetricKey TIME = MetricKey.of("TIME");
    public static final MetricKey ACCURACY = MetricKey.of("ACCURACY");
    public static final MetricKey CREATIVITY = MetricKey.of("CREATIVITY");
    public static final MetricKey EXECUTION = MetricKey.of("EXECUTION");
    public static final PenaltyCode RESTART = PenaltyCode.of("RESTART");
    public static final JudgeId J1 = JudgeId.of("J1");
    public static final JudgeId J2 = JudgeId.of("J2");

    private ShowcaseFixture() {
    }

    public static ChallengeSpec mixedShowcase() {
        return showcase(scoringRules(), MetricRequirement.REQUIRED, BonusPolicy.uncapped(),
                new MixedSources());
    }

    public static ChallengeSpec singleCaptureShowcase() {
        return showcase(scoringRules(), MetricRequirement.OPTIONAL, BonusPolicy.uncapped(),
                CaptureMode.single());
    }

    public static ChallengeSpec showcase(List<ScoringRule> scoringRules, MetricRequirement criteria,
            BonusPolicy bonusPolicy, CaptureMode captureMode) {
        return ChallengeSpec.builder(CHALLENGE_ID, "Showcase")
                .metrics(metrics(criteria))
                .scoringRules(scoringRules)
                .penalties(penalties())
                .maximumAttempts(AttemptLimit.of(2))
                .bonusPolicy(bonusPolicy)
                .captureMode(captureMode)
                .build();
    }

    public static List<MetricDefinition> metrics(MetricRequirement criteria) {
        return List.of(
                MetricDefinition.required(TIME, MetricKind.TIME_SECONDS, MetricUnit.of("s")),
                MetricDefinition.required(ACCURACY, MetricKind.PRECISION_RATIO, MetricUnit.of("ratio")),
                new MetricDefinition(CREATIVITY, MetricKind.JUDGE_CRITERION, MetricUnit.of("points"), criteria),
                new MetricDefinition(EXECUTION, MetricKind.JUDGE_CRITERION, MetricUnit.of("points"), criteria));
    }

    public static List<ScoringRule> scoringRules() {
        return List.of(
                new TimeScoringRule(TIME, Duration.ofSeconds(90), PointsRate.of("0.50"), PointsCap.of(20)),
                new PrecisionScoringRule(ACCURACY, PointsCap.of(30)),
                new JudgePanelScoringRule(CREATIVITY, PointsRate.of(2)),
                new JudgePanelScoringRule(EXECUTION, PointsRate.of("1.50")));
    }

    public static List<PenaltyDefinition> penalties() {
        return List.of(new PenaltyDefinition(RESTART, "manual restart", PointsDeducted.of(3)));
    }

    public static RulebookDraft rulebook(ChallengeSpec showcase) {
        return RescueEditionFixture.rulebook(List.of(RescueEditionFixture.rescueChallenge(), showcase),
                RescueEditionFixture.attemptAggregation());
    }

    public static MeasurementSet measurements(String seconds, String accuracy) {
        return MeasurementSet.empty()
                .with(TIME, MetricValue.of(seconds))
                .with(ACCURACY, MetricValue.of(accuracy));
    }

    public static MeasurementSet exampleMeasurements() {
        return measurements("70", "0.80");
    }

    public static JudgeEvaluations evaluations(int j1Creativity, int j1Execution, int j2Creativity,
            int j2Execution) {
        return JudgeEvaluations.of(
                evaluation(J1, CREATIVITY, j1Creativity),
                evaluation(J1, EXECUTION, j1Execution),
                evaluation(J2, CREATIVITY, j2Creativity),
                evaluation(J2, EXECUTION, j2Execution));
    }

    public static JudgeEvaluations exampleEvaluations() {
        return evaluations(8, 9, 6, 7);
    }

    public static List<IncidentReport> exampleIncidents() {
        return List.of(IncidentReport.once(RESTART));
    }

    public static ScoringContext exampleContext() {
        return new ScoringContext(exampleMeasurements(), exampleEvaluations(), exampleIncidents());
    }

    public static JudgeEvaluation evaluation(JudgeId judge, MetricKey criterion, int score) {
        return new JudgeEvaluation(judge, criterion, JudgeScore.of(score));
    }

    public static Round scheduledRound(String roundId, String heatId, TeamId team) {
        RoundId id = RoundId.of(roundId);
        TeamRegistration registration = new TeamRegistration(team, CompetitionId.of("COMP-1"),
                CategoryId.of("CAT-1"), "Delta Bots", TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot(),
                TeamFixtures.completeDocuments()).resolveWith(new EligibilityVerdict(List.of()));
        return new Round(id, CompetitionId.of("COMP-1"), CategoryId.of("CAT-1"), CHALLENGE_ID, RoundOrdinal.of(1),
                RulebookVersion.first())
                .schedule(new Heat(HeatId.of(heatId), id, team, ArenaId.of("A1"),
                        new TimeSlot(LocalDateTime.of(2026, 3, 2, 10, 0), Duration.ofMinutes(15)),
                        Set.of(J1, J2)), registration);
    }

    public record ReadingRule(Set<MetricKey> referencedMetrics) implements ScoringRule {

        public static final ScoringRuleCode CODE = ScoringRuleCode.of("READING");

        public ReadingRule {
            referencedMetrics = Set.copyOf(referencedMetrics);
        }

        @Override
        public List<ScoreContribution> apply(ScoringContext context) {
            return List.of(ScoreContribution.earned(CODE, "reads " + referencedMetrics.size() + " metric(s)",
                    Points.ZERO));
        }
    }
}
