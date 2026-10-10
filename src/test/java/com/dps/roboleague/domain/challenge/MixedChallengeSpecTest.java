package com.dps.roboleague.domain.challenge;

import static com.dps.roboleague.support.ShowcaseFixture.ACCURACY;
import static com.dps.roboleague.support.ShowcaseFixture.CREATIVITY;
import static com.dps.roboleague.support.ShowcaseFixture.EXECUTION;
import static com.dps.roboleague.support.ShowcaseFixture.J1;
import static com.dps.roboleague.support.ShowcaseFixture.J2;
import static com.dps.roboleague.support.ShowcaseFixture.TIME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.scoring.BonusCap;
import com.dps.roboleague.domain.scoring.BonusPoints;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.ScoringRuleCode;
import com.dps.roboleague.domain.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.domain.scoring.rule.PenaltyScoringRule;
import com.dps.roboleague.domain.scoring.rule.PrecisionScoringRule;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.scoring.rule.TimeScoringRule;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.ShowcaseFixture;
import com.dps.roboleague.support.ShowcaseFixture.ReadingRule;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MixedChallengeSpecTest {

    private static final Set<JudgeId> PANEL = Set.of(J1, J2);
    private static final Optional<MixedSources> MIXED = Optional.of(new MixedSources());

    private final ChallengeSpec mixed = ShowcaseFixture.mixedShowcase();

    @Test
    void everyMetricKindDeclaresTheSourceItArrivesFrom() {
        assertEquals(List.of(ResultSource.AUTOMATIC, ResultSource.AUTOMATIC, ResultSource.AUTOMATIC,
                        ResultSource.AUTOMATIC, ResultSource.JUDGES),
                Arrays.stream(MetricKind.values()).map(MetricKind::source).toList());
    }

    @Test
    void aMixedChallengeRequiresScoringRulesOfBothSources() {
        List<ScoringRule> onlyAutomatic = ShowcaseFixture.scoringRules().subList(0, 2);
        List<ScoringRule> onlyPanel = ShowcaseFixture.scoringRules().subList(2, 4);

        InvalidValueException withoutPanel = assertThrows(InvalidValueException.class,
                () -> mixedWith(onlyAutomatic));
        InvalidValueException withoutAutomatic = assertThrows(InvalidValueException.class,
                () -> mixedWith(onlyPanel));

        assertTrue(withoutPanel.getMessage().contains("JUDGES"));
        assertTrue(withoutAutomatic.getMessage().contains("AUTOMATIC"));
    }

    @Test
    void aMixedChallengeRejectsARuleThatReadsBothSources() {
        List<ScoringRule> rules = List.of(new ReadingRule(Set.of(TIME, CREATIVITY)),
                new JudgePanelScoringRule(EXECUTION, PointsRate.of(1)));

        InvalidValueException error = assertThrows(InvalidValueException.class, () -> mixedWith(rules));

        assertTrue(error.getMessage().contains("CREATIVITY, TIME"));
        assertDoesNotThrow(() -> ShowcaseFixture.showcase(rules, MetricRequirement.OPTIONAL, Optional.empty(),
                Optional.empty()));
    }

    @Test
    void aRuleThatReadsNoMetricBelongsToTheAutomaticSource() {
        ChallengeSpec challenge = mixedWith(List.of(new ReadingRule(Set.of()),
                new JudgePanelScoringRule(CREATIVITY, PointsRate.of(1))));

        SourcedScore score = challenge.scoreBySource(ShowcaseFixture.exampleContext()).orElseThrow();

        assertEquals(List.of(ReadingRule.CODE), codesOf(score.sources().getFirst().breakdown()));
    }

    @Test
    void onlyAMixedChallengeTakesItsSourcesSeparately() {
        ChallengeSpec single = ShowcaseFixture.singleCaptureShowcase();

        assertThrows(RuleViolationException.class, mixed::requireSingleCapture);
        assertDoesNotThrow(single::requireSingleCapture);
        assertThrows(RuleViolationException.class,
                () -> single.validateAutomaticSource(ShowcaseFixture.exampleMeasurements()));
        assertThrows(RuleViolationException.class, () -> single.validatePanelSource(
                ShowcaseFixture.exampleEvaluations(), PANEL, List.of()));
    }

    @Test
    void theAutomaticSourceNeedsEveryRequiredMeasurementButNeverAJudgeCriterion() {
        assertDoesNotThrow(() -> mixed.validateAutomaticSource(ShowcaseFixture.exampleMeasurements()));

        RuleViolationException criterion = assertThrows(RuleViolationException.class,
                () -> mixed.validateAutomaticSource(ShowcaseFixture.exampleMeasurements()
                        .with(CREATIVITY, MetricValue.of(8))));
        RuleViolationException missing = assertThrows(RuleViolationException.class,
                () -> mixed.validateAutomaticSource(MeasurementSet.empty().with(TIME, MetricValue.of(70))));

        assertTrue(criterion.getMessage().contains("CREATIVITY"));
        assertTrue(missing.getMessage().contains("ACCURACY"));
        assertThrows(RuleViolationException.class,
                () -> mixed.validateAutomaticSource(ShowcaseFixture.measurements("70", "1.5")));
        assertThrows(RuleViolationException.class, () -> mixed.validateAutomaticSource(
                ShowcaseFixture.exampleMeasurements().with(MetricKey.of("BATTERY"), MetricValue.of(1))));
    }

    @Test
    void aSingleCaptureChallengeKeepsMeasuringItsJudgeCriteria() {
        ChallengeSpec single = ShowcaseFixture.singleCaptureShowcase();

        assertDoesNotThrow(() -> single.validate(ShowcaseFixture.exampleMeasurements()
                .with(CREATIVITY, MetricValue.of(8))));
    }

    @Test
    void thePanelSourceNeedsAnEvaluationOfEveryJudgeForEveryCriterion() {
        JudgeEvaluations withoutJ2Execution = JudgeEvaluations.of(
                ShowcaseFixture.evaluation(J1, CREATIVITY, 8), ShowcaseFixture.evaluation(J1, EXECUTION, 9),
                ShowcaseFixture.evaluation(J2, CREATIVITY, 6));

        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> mixed.validatePanelSource(withoutJ2Execution, PANEL, List.of()));

        assertTrue(error.getMessage().contains("J2"));
        assertTrue(error.getMessage().contains("EXECUTION"));
        assertDoesNotThrow(() -> mixed.validatePanelSource(ShowcaseFixture.exampleEvaluations(), PANEL,
                ShowcaseFixture.exampleIncidents()));
        assertDoesNotThrow(() -> mixed.validatePanelSource(ShowcaseFixture.evaluations(0, 0, 0, 0), PANEL,
                List.of()));
    }

    @Test
    void thePanelSourceKeepsTheValidationsOfTheCapture() {
        JudgeEvaluations outsider = JudgeEvaluations.of(ShowcaseFixture.evaluation(JudgeId.of("J3"), CREATIVITY, 8));
        JudgeEvaluations notACriterion = JudgeEvaluations.of(ShowcaseFixture.evaluation(J1, TIME, 8));
        List<IncidentReport> unknownIncident = List.of(IncidentReport.once(PenaltyCode.of("SABOTAGE")));

        assertThrows(RuleViolationException.class, () -> mixed.validatePanelSource(outsider, PANEL, List.of()));
        assertThrows(RuleViolationException.class,
                () -> mixed.validatePanelSource(notACriterion, PANEL, List.of()));
        assertThrows(RuleViolationException.class, () -> mixed.validatePanelSource(
                ShowcaseFixture.exampleEvaluations(), PANEL, unknownIncident));
    }

    @Test
    void groupsTheContributionsBySourceWithTheIncidentsInThePanel() {
        SourcedScore score = mixed.scoreBySource(ShowcaseFixture.exampleContext()).orElseThrow();

        SourceBreakdown automatic = score.sources().get(0);
        SourceBreakdown panel = score.sources().get(1);
        assertEquals(ResultSource.AUTOMATIC, automatic.source());
        assertEquals(List.of(TimeScoringRule.CODE, PrecisionScoringRule.CODE), codesOf(automatic.breakdown()));
        assertEquals(Points.of(34), automatic.subtotal());
        assertEquals(ResultSource.JUDGES, panel.source());
        assertEquals(List.of(JudgePanelScoringRule.CODE, JudgePanelScoringRule.CODE, PenaltyScoringRule.CODE),
                codesOf(panel.breakdown()));
        assertEquals(List.of(Points.of(14), Points.of(12), Points.of(-3)), pointsOf(panel.breakdown()));
        assertEquals(Points.of(23), panel.subtotal());
        assertTrue(score.acrossSources().contributions().isEmpty());
        assertEquals(Points.of(57), score.total());
        assertEquals(mixed.score(ShowcaseFixture.exampleContext()).total(), score.total());
    }

    @Test
    void theBonusCapIsAGroupApartFromBothSources() {
        List<ScoringRule> rules = List.of(
                new ThresholdBonusRule(TIME, ThresholdBonusRule.Comparison.AT_MOST, MetricValue.of(80),
                        BonusPoints.of(10)),
                new ThresholdBonusRule(ACCURACY, ThresholdBonusRule.Comparison.AT_LEAST, MetricValue.of("0.5"),
                        BonusPoints.of(10)),
                new JudgePanelScoringRule(CREATIVITY, PointsRate.of(1)));
        ChallengeSpec capped = ShowcaseFixture.showcase(rules, MetricRequirement.REQUIRED,
                Optional.of(BonusCap.of(15)), MIXED);

        SourcedScore score = capped.scoreBySource(ShowcaseFixture.exampleContext()).orElseThrow();
        ScoreBreakdown flat = capped.score(ShowcaseFixture.exampleContext());

        assertEquals(Points.of(20), score.sources().getFirst().subtotal());
        assertEquals(List.of(BonusCap.CODE), codesOf(score.acrossSources()));
        assertEquals(Points.of(-5), score.acrossSources().total());
        assertEquals(flat.total(), score.total());
        assertEquals(Points.of(15), flat.totalOf(ContributionKind.BONUS));
    }

    @Test
    void aSingleCaptureChallengeHasNoScoreBySource() {
        assertTrue(RescueEditionFixture.rescueChallenge().scoreBySource(ShowcaseFixture.exampleContext()).isEmpty());
    }

    @Test
    void aSourcedScoreGroupsEachSourceOnce() {
        SourceBreakdown automatic = new SourceBreakdown(ResultSource.AUTOMATIC, new ScoreBreakdown(List.of()));
        ScoreBreakdown none = new ScoreBreakdown(List.of());

        assertThrows(InvalidValueException.class, () -> new SourcedScore(List.of(automatic, automatic), none));
    }

    private static ChallengeSpec mixedWith(List<ScoringRule> rules) {
        return ShowcaseFixture.showcase(rules, MetricRequirement.REQUIRED, Optional.empty(), MIXED);
    }

    private static List<ScoringRuleCode> codesOf(ScoreBreakdown breakdown) {
        return breakdown.contributions().stream().map(ScoreContribution::ruleCode).toList();
    }

    private static List<Points> pointsOf(ScoreBreakdown breakdown) {
        return breakdown.contributions().stream().map(ScoreContribution::points).toList();
    }
}
